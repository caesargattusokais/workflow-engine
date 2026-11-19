package com.github.wf.engine.runner;

import com.github.wf.engine.*;
import com.github.wf.ext.AiTaskHandler;
import com.github.wf.model.*;
import com.github.wf.model.node.AiTask;
import com.github.wf.spi.InstanceRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs an AI_TASK node. The actual LLM call is delegated to an
 * {@link AiTaskHandler} supplied by the host via registration or ServiceLoader,
 * so the engine core carries no LLM dependency. A missing handler is a hard
 * error that flows through the normal retry/exception/suspend path — there is
 * no silent "AI did nothing" outcome.
 */
public class AiTaskRunner implements NodeRunner {

    private static final Log log = LogFactory.getLog(AiTaskRunner.class);

    private final Map<String, AiTaskHandler> handlerRegistry = new ConcurrentHashMap<>();
    private final java.util.function.BiConsumer<String, Long> scheduler;

    public AiTaskRunner() { this(null); }

    public AiTaskRunner(java.util.function.BiConsumer<String, Long> scheduler) {
        this.scheduler = scheduler;
    }

    public void registerHandler(String name, AiTaskHandler handler) {
        handlerRegistry.put(name, handler);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        AiTask task = (AiTask) node;
        String ns = node.getId() + "_";
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();
        ProcessInstance instance = repo.findById(context.getInstanceId());
        Map<String, Object> vars = new HashMap<>(instance.getVariables());

        log.info("Running AiTask " + exec.getInstanceId() + " node=" + node.getId()
                + " mode=" + task.getMode());

        try {
            AiTaskHandler handler = resolveHandler();
            String system = systemPrompt(task);
            String user = resolveTemplate(task.getPrompt(), vars);

            Map<String, Object> result = handler.execute(system, user, vars);
            if (result == null) result = Collections.emptyMap();

            // Normalise the raw answer: the handler always puts it under "text".
            String text = String.valueOf(result.getOrDefault("text", ""));
            if ("classify".equals(task.getMode())) {
                String category = text.trim().replaceAll("^\"|^\'|\"$|\'$", "");
                if (!task.getOptions().isEmpty() && !task.getOptions().contains(category)) {
                    log.warn("AI_TASK " + node.getId() + " returned category '" + category
                            + "' outside options " + task.getOptions());
                }
                result = new LinkedHashMap<>(result);
                result.put("category", category);
            }

            // Namespace every key by node id; also expose the whole map.
            for (Map.Entry<String, Object> e : result.entrySet()) {
                instance.setVariable(ns + e.getKey(), e.getValue());
                vars.put(ns + e.getKey(), e.getValue());
            }
            vars.put(ns + "result", result);
            instance.setVariable(ns + "result", result);
            if (task.getOutputVar() != null && !task.getOutputVar().isBlank()) {
                vars.put(task.getOutputVar(), result);
                instance.setVariable(task.getOutputVar(), result);
            }
            repo.update(instance);
            exec.setRetryAttempt(0);

            // Success routing: result edges (condition-aware) -> direct edge.
            for (Transition t : context.getDefinition().getOutgoingTransitions(node.getId())) {
                if (!t.isResult()) continue;
                if (t.getCondition() == null
                        || ConditionResolver.evaluate(t.getCondition(), vars, context.getExpressionEvaluator())) {
                    exec.setCurrentNodeId(t.getTo());
                    return true;
                }
            }
            for (Transition t : context.getDefinition().getOutgoingTransitions(node.getId())) {
                if (t.isDirect()) {
                    exec.setCurrentNodeId(t.getTo());
                    return true;
                }
            }
            return true;

        } catch (Exception e) {
            exec.setRetryAttempt(exec.getRetryAttempt() + 1);
            ExceptionInfo ei = new ExceptionInfo(e);
            vars.put("exception", ei);
            log.warn("AiTask " + node.getId() + " failed (attempt " + exec.getRetryAttempt() + "): "
                    + e.getMessage());

            RetryConfig rc = task.getRetryConfig();
            if (rc != null && exec.getRetryAttempt() < rc.getMaxAttempts()
                    && shouldRetry(rc.getRetryOn(), vars, context)) {
                long delay = rc.calculateDelay(exec.getRetryAttempt() - 1);
                repo.update(instance);
                if (scheduler != null) {
                    scheduler.accept(exec.getInstanceId(), delay);
                    log.info("Scheduling AI retry for instance " + exec.getInstanceId()
                            + " in " + delay + "ms");
                }
                exec.setStatus(ExecutionStatus.WAITING);
                exec.setRetryState(RetryState.RETRY_PENDING.name());
                repo.updateExecution(exec);
                return true;
            }

            // Exception edges (catch-all first, then conditions).
            for (Transition t : context.getDefinition().getOutgoingTransitions(node.getId())) {
                if (!t.isException()) continue;
                if (t.getCondition() == null
                        || ConditionResolver.evaluate(t.getCondition(), vars, context.getExpressionEvaluator())) {
                    exec.setCurrentNodeId(t.getTo());
                    exec.setRetryAttempt(0);
                    exec.setRetryState(null);
                    return true;
                }
            }

            // No route — suspend with a human-readable reason (same mechanism
            // as ServiceTask), so the ops copilot can explain it later.
            exec.setRetryState(RetryState.SUSPENDED.name());
            exec.setStatus(ExecutionStatus.WAITING);
            exec.setRetryAttempt(0);
            repo.updateExecution(exec);
            instance.setVariable(InternalVars.suspendReasonKey(node.getId()),
                    "AI task failed: " + ei.getMessage());
            instance.setVariable(InternalVars.suspendExceptionKey(node.getId()), ei.getType());
            repo.update(instance);
            return true;
        }
    }

    /** Mode-specific instructions; the body of the prompt stays user-editable. */
    static String systemPrompt(AiTask task) {
        return switch (task.getMode()) {
            case "extract" -> "You extract structured data. Return ONLY a JSON object whose keys are "
                    + "the requested field names and values are the extracted values. "
                    + "Escape quotes correctly. Do not add commentary or markdown fences.";
            case "classify" -> "You classify text into exactly one category. Reply with ONLY the "
                    + "category name, nothing else. Valid categories: "
                    + String.join(", ", task.getOptions()) + ".";
            default -> "You summarise the given content faithfully and concisely. "
                    + "Reply with the summary text only, no preamble.";
        };
    }

    /** Resolve ${name} placeholders from process variables. */
    static String resolveTemplate(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("${", i);
            if (start < 0) { out.append(template, i, template.length()); break; }
            int end = template.indexOf('}', start + 2);
            if (end < 0) { out.append(template, i, template.length()); break; }
            out.append(template, i, start);
            String key = template.substring(start + 2, end).trim();
            Object v = vars.get(key);
            out.append(v == null ? "" : v);
            i = end + 1;
        }
        return out.toString();
    }

    private AiTaskHandler resolveHandler() {
        // 1. Explicitly registered (server overrides via engine.registerAiHandler).
        for (AiTaskHandler h : handlerRegistry.values()) return h;
        // 2. ServiceLoader — the production bridge from server to core.
        Iterator<AiTaskHandler> it = ServiceLoader.load(AiTaskHandler.class).iterator();
        if (it.hasNext()) return it.next();
        throw new IllegalStateException("No AiTaskHandler available — AI is not configured");
    }

    private boolean shouldRetry(List<Condition> retryOn, Map<String, Object> vars,
                                ExecutionContext context) {
        if (retryOn.isEmpty()) return true;
        for (Condition c : retryOn) {
            if (ConditionResolver.evaluate(c, vars, context.getExpressionEvaluator())) return true;
        }
        return false;
    }
}

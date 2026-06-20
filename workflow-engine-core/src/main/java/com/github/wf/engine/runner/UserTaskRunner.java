package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.OrgService;
import com.github.wf.ext.http.HttpClientUtil;
import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessInstance;
import com.github.wf.model.Transition;
import com.github.wf.model.node.UserTask;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class UserTaskRunner implements NodeRunner {

    private static final Log log = LogFactory.getLog(UserTaskRunner.class);

    private final TaskRepository taskRepository;
    private final BiConsumer<String, Long> scheduler;
    private final String baseUrl;
    private final OrgService orgService;

    public UserTaskRunner(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = null;
        this.baseUrl = null;
        this.orgService = null;
    }

    public UserTaskRunner(TaskRepository taskRepository, BiConsumer<String, Long> scheduler) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = scheduler;
        this.baseUrl = null;
        this.orgService = null;
    }

    public UserTaskRunner(TaskRepository taskRepository, BiConsumer<String, Long> scheduler, String baseUrl) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = scheduler;
        this.baseUrl = baseUrl;
        this.orgService = null;
    }

    public UserTaskRunner(TaskRepository taskRepository, BiConsumer<String, Long> scheduler, String baseUrl, OrgService orgService) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = scheduler;
        this.baseUrl = baseUrl;
        this.orgService = orgService;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        UserTask userTask = (UserTask) node;
        Execution exec = context.getExecution();
        Map<String, Object> variables = context.getVariables();

        // Check if re-entering after boundary timer timeout
        String timerKey = node.getId() + "_boundaryTimerFired";
        ProcessInstance instance = context.getInstanceRepository().findById(context.getInstanceId());
        if (Boolean.TRUE.equals(instance.getVariable(timerKey))) {
            log.warn("Boundary timer fired for node=" + node.getId() + " instance=" + context.getInstanceId());
            instance.removeVariable(timerKey);
            context.getInstanceRepository().update(instance);
            for (Transition t : context.getDefinition().getOutgoingTransitions(node.getId())) {
                if (t.isTimeout()) {
                    log.warn("Timeout edge matched, routing to " + t.getTo());
                    exec.setCurrentNodeId(t.getTo());
                    exec.setStatus(ExecutionStatus.ACTIVE);
                    return true;
                }
            }
            log.warn("No timeout edge found for node=" + node.getId() + " — falling through");
        }

        // Check if a pending task already exists for this execution+node
        boolean taskExists = taskRepository.query(
                new com.github.wf.task.TaskQuery().instanceId(exec.getInstanceId()))
                .stream()
                .anyMatch(t -> t.getNodeId().equals(node.getId()) && t.isPending());

        if (taskExists) {
            return false;
        }

        // Create task
        Task task = new Task(null, exec.getInstanceId(), node.getId());

        // Evaluate assignee expression or use literal
        if (userTask.getAssignee() != null) {
            String assigneeExpr = userTask.getAssignee();
            if (assigneeExpr.startsWith("${") && assigneeExpr.endsWith("}")) {
                assigneeExpr = assigneeExpr.substring(2, assigneeExpr.length() - 1);
                // Support ${variable}.manager → resolve variable then look up manager via OrgService
                if (assigneeExpr.endsWith(".manager") && orgService != null) {
                    String varName = assigneeExpr.substring(0, assigneeExpr.length() - 8);
                    Object val = context.getExpressionEvaluator().evaluate(varName, variables);
                    if (val != null) {
                        String mgr = orgService.getManager(val.toString());
                        task.setAssignee(mgr != null ? mgr : val.toString());
                    }
                } else {
                    Object assignee = context.getExpressionEvaluator().evaluate(assigneeExpr, variables);
                    task.setAssignee(assignee != null ? assignee.toString() : null);
                }
            } else {
                task.setAssignee(assigneeExpr);
            }
        }

        task.setCandidateGroups(userTask.getCandidateGroups());
        task.setVariables(new java.util.HashMap<>(variables));
        taskRepository.save(task);

        // ── HTTP callback mode ─────────────────
        if (userTask.isHttpTask() && userTask.getUrl() != null && !userTask.getUrl().isBlank()) {
            String taskId = task.getId();
            Map<String, Object> httpVars = new HashMap<>(variables);
            httpVars.put("taskId", taskId);
            httpVars.put("instanceId", exec.getInstanceId());
            httpVars.put("nodeId", node.getId());

            // Build callback headers — always passed, regardless of body
            Map<String, String> callHeaders = new HashMap<>(userTask.getHeaders());
            if (baseUrl != null && !baseUrl.isBlank()) {
                String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                String completeUrl = base + "/api/tasks/" + taskId + "/complete";
                String rejectUrl = base + "/api/tasks/" + taskId + "/reject";
                httpVars.put("completeUrl", completeUrl);
                httpVars.put("rejectUrl", rejectUrl);
                callHeaders.put("X-Callback-Complete", completeUrl);
                callHeaders.put("X-Callback-Reject", rejectUrl);
                callHeaders.put("X-Task-Id", taskId);
            }
            log.warn("Sending HTTP callback for task " + taskId + " to " + userTask.getUrl());
            try {
                HttpClientUtil.fireAndForget(userTask.getUrl(), userTask.getMethod(),
                        callHeaders, userTask.getBody(), httpVars);
            } catch (Exception e) {
                log.error("HTTP callback failed for task " + taskId + ": " + e.getMessage(), e);
            }
        }

        exec.setStatus(ExecutionStatus.WAITING);

        // Schedule boundary timer if configured
        if (userTask.getBoundaryTimer() != null && !userTask.getBoundaryTimer().isBlank() && scheduler != null) {
            try {
                long delayMs = Duration.parse(userTask.getBoundaryTimer()).toMillis();
                if (delayMs > 0) {
                    log.warn("Scheduling boundary timer for node=" + node.getId() + " delay=" + delayMs + "ms");
                    instance.setVariable(timerKey, true);
                    context.getInstanceRepository().update(instance);
                    scheduler.accept(exec.getInstanceId(), delayMs);
                    exec.setRetryState("TIMER_PENDING");
                }
            } catch (Exception e) {
                // Invalid duration format — skip boundary timer
            }
        }

        return true;
    }
}

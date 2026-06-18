package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.ServiceTaskHandler;
import com.github.wf.model.*;
import com.github.wf.model.node.ServiceTask;
import com.github.wf.spi.InstanceRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceTaskRunner implements NodeRunner {

    private final Map<String, ServiceTaskHandler> handlerRegistry = new ConcurrentHashMap<>();
    private final java.util.function.BiConsumer<String, Long> retryScheduler;

    public ServiceTaskRunner() { this.retryScheduler = null; }

    public ServiceTaskRunner(java.util.function.BiConsumer<String, Long> retryScheduler) {
        this.retryScheduler = retryScheduler;
    }

    public void registerHandler(String className, ServiceTaskHandler handler) {
        handlerRegistry.put(className, handler);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        ServiceTask serviceTask = (ServiceTask) node;
        String ns = node.getId() + "_";
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        ProcessInstance instance = repo.findById(context.getInstanceId());
        Map<String, Object> variables = new HashMap<>(instance.getVariables());

        try {
            boolean httpMode = serviceTask.isHttpTask();
            boolean hasUrl = serviceTask.getUrl() != null && !serviceTask.getUrl().isBlank();
            String hc = serviceTask.getHandlerClass();

            if (httpMode) {
                if (!hasUrl) throw new RuntimeException("HTTP ServiceTask missing URL");
            } else {
                if (hc == null || hc.isEmpty()) throw new RuntimeException("Code ServiceTask missing handlerClass");
            }

            Map<String, Object> result;
            if (hasUrl) {
                result = executeHttp(serviceTask, variables);
            } else {
                result = getHandler(hc).execute(variables);
            }

            // Success — namespace result by node ID to avoid conflicts
            if (result != null) {
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    instance.setVariable(ns + e.getKey(), e.getValue());
                    variables.put(ns + e.getKey(), e.getValue());
                }
                // Also store the full map for routing: nodeId.result['key']
                variables.put(ns + "result", result);
                instance.setVariable(ns + "result", result);
                repo.update(instance);
            }
            exec.setRetryAttempt(0);

            // Result routing
            List<RoutingRule> resultRoutes = serviceTask.getResultRouting();
            if (!resultRoutes.isEmpty()) {
                for (RoutingRule rule : resultRoutes) {
                    boolean match;
                    if (rule.isDefault()) {
                        match = true;
                    } else {
                        match = evaluateCondition(rule.getCondition(), variables, context);
                    }
                    if (match) {
                        exec.setCurrentNodeId(rule.getTo());
                        return true;
                    }
                }
            }

            // No result routing match → static transition
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (!outgoing.isEmpty()) {
                exec.setCurrentNodeId(outgoing.get(0).getTo());
            }
            return true;

        } catch (Exception e) {
            exec.setRetryAttempt(exec.getRetryAttempt() + 1);

            // Build exception context
            ExceptionInfo ei = new ExceptionInfo(e);
            variables.put("exception", ei);

            // Check retry — schedule via delay queue, non-blocking
            RetryConfig rc = serviceTask.getRetryConfig();
            if (rc != null && exec.getRetryAttempt() < rc.getMaxAttempts()) {
                if (shouldRetry(rc.getRetryOn(), variables, context)) {
                    long delay = rc.calculateDelay(exec.getRetryAttempt() - 1);
                    if (retryScheduler != null) {
                        retryScheduler.accept(exec.getInstanceId(), delay);
                    }
                    exec.setStatus(ExecutionStatus.WAITING);
                    repo.updateExecution(exec);
                    return true;
                }
            }

            // Exception routing
            List<RoutingRule> exceptionRoutes = serviceTask.getExceptionRouting();
            if (!exceptionRoutes.isEmpty()) {
                for (RoutingRule rule : exceptionRoutes) {
                    boolean match;
                    if (rule.isDefault()) {
                        match = true; // default — always matches
                    } else {
                        match = evaluateCondition(rule.getCondition(), variables, context);
                    }
                    if (match) {
                        exec.setCurrentNodeId(rule.getTo());
                        exec.setRetryAttempt(0);
                        exec.setRetryState(null);
                        return true;
                    }
                }
            }

            // No exception route matched → SUSPEND the instance
            exec.setRetryState("SUSPENDED");
            exec.setStatus(ExecutionStatus.WAITING);
            exec.setRetryAttempt(0);
            repo.updateExecution(exec);

            // Set suspend reason in instance variables
            instance.setVariable(ns+"suspendReason", ei.getMessage());
            instance.setVariable(ns+"suspendException", ei.getType());
            repo.update(instance);

            return true;
        }
    }

    private ServiceTaskHandler getHandler(String handlerClass) {
        ServiceTaskHandler handler = handlerRegistry.get(handlerClass);
        if (handler == null) {
            try {
                Class<?> clazz = Class.forName(handlerClass);
                handler = (ServiceTaskHandler) clazz.getDeclaredConstructor().newInstance();
                handlerRegistry.put(handlerClass, handler);
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate ServiceTaskHandler: " + handlerClass, e);
            }
        }
        return handler;
    }

    /**
     * Execute an HTTP service task using built-in java.net.http.HttpClient.
     * No handlerClass needed — reads url/method/headers/body from node config.
     */
    private Map<String, Object> executeHttp(ServiceTask task, Map<String, Object> variables) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build();

            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(interpolate(task.getUrl(), variables)))
                    .timeout(java.time.Duration.ofSeconds(30));

            // Headers
            for (var entry : task.getHeaders().entrySet()) {
                builder.header(entry.getKey(), interpolate(entry.getValue(), variables));
            }

            // Method + body
            String method = task.getMethod().toUpperCase();
            String bodyStr = task.getBody() != null ? interpolate(task.getBody(), variables) : null;

            if (bodyStr != null && !bodyStr.isEmpty()
                    && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                builder.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(
                        bodyStr, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                builder.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
            }

            java.net.http.HttpResponse<String> response = client.send(builder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + ": " + response.body());
            }

            if (response.body() == null || response.body().isBlank()) {
                return Map.of("statusCode", status, "body", "");
            }

            // Parse JSON response
            com.google.gson.Gson gson = new com.google.gson.Gson();
            var type = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> result = gson.fromJson(response.body(), type);
            result.put("statusCode", status);
            return result;

        } catch (java.net.http.HttpTimeoutException e) {
            throw new com.github.wf.ext.http.HttpTimeoutException("HTTP timeout: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP interrupted", e);
        }
    }

    /** Simple ${var} interpolation in strings */
    private String interpolate(String template, Map<String, Object> vars) {
        if (template == null) return null;
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    /**
     * Check if any retryOn condition matches. If retryOn is empty, retry any exception.
     */
    private boolean shouldRetry(List<Condition> retryOn, Map<String, Object> variables,
                                 ExecutionContext context) {
        if (retryOn.isEmpty()) {
            return true; // retry all exceptions
        }
        for (Condition c : retryOn) {
            if (evaluateCondition(c, variables, context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluate a Condition using either SpEL expression or Java class.
     */
    private boolean evaluateCondition(Condition condition, Map<String, Object> variables,
                                       ExecutionContext context) {
        if (condition.getType() == ConditionType.EXPRESSION) {
            return context.getExpressionEvaluator().evaluateToBoolean(condition.getExpr(), variables);
        } else {
            try {
                Class<?> clazz = Class.forName(condition.getClassName());
                com.github.wf.ext.ConditionEvaluator evaluator =
                        (com.github.wf.ext.ConditionEvaluator) clazz.getDeclaredConstructor().newInstance();
                return evaluator.evaluate(variables);
            } catch (Exception e) {
                throw new RuntimeException("Cannot evaluate condition: " + condition.getClassName(), e);
            }
        }
    }
}

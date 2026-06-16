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

    public void registerHandler(String className, ServiceTaskHandler handler) {
        handlerRegistry.put(className, handler);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        ServiceTask serviceTask = (ServiceTask) node;
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        ProcessInstance instance = repo.findById(context.getInstanceId());
        Map<String, Object> variables = new HashMap<>(instance.getVariables());

        try {
            ServiceTaskHandler handler = getHandler(serviceTask.getHandlerClass());
            Map<String, Object> result = handler.execute(variables);

            // Success — merge result into instance variables
            if (result != null) {
                instance.setVariables(result);
                repo.update(instance);
                // Put the full result map for expressions like result['key'] or result.key
                variables.put("result", result);
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

            // Check retry
            RetryConfig rc = serviceTask.getRetryConfig();
            if (rc != null && exec.getRetryAttempt() < rc.getMaxAttempts()) {
                if (shouldRetry(rc.getRetryOn(), variables, context)) {
                    long delay = rc.calculateDelay(exec.getRetryAttempt() - 1);
                    exec.setNextRetryAt(System.currentTimeMillis() + delay);
                    exec.setStatus(ExecutionStatus.WAITING);
                    repo.updateExecution(exec);
                    return true; // waiting for retry timer
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
            instance.setVariable("_suspendReason", ei.getMessage());
            instance.setVariable("_suspendException", ei.getType());
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

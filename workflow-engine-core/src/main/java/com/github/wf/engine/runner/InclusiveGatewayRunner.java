package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.ConditionEvaluator;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import java.util.List;
import java.util.Map;

public class InclusiveGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> incoming = context.getDefinition().getIncomingTransitions(node.getId());
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());

        boolean isFork = incoming.size() <= 1 || outgoing.size() > 1;

        if (isFork && outgoing.size() > 1) {
            return handleFork(context, outgoing);
        } else {
            return handleJoin(node, context);
        }
    }

    private boolean handleFork(ExecutionContext context, List<Transition> outgoing) {
        Execution parent = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();
        Map<String, Object> variables = context.getVariables();

        int forked = 0;
        for (Transition t : outgoing) {
            boolean match;
            if (t.isConditional()) {
                match = evaluateCondition(t.getCondition(), variables, context);
            } else if (t.isDefault()) {
                match = (forked == 0);
            } else {
                match = true;
            }

            if (match) {
                Execution child = new Execution(null, parent.getInstanceId(), t.getTo(), parent.getId());
                repo.saveExecution(child);
                forked++;
            }
        }

        if (forked == 0) {
            throw new IllegalStateException("No outgoing transition matched for inclusive gateway: " +
                    parent.getCurrentNodeId());
        }

        if (forked == 1) {
            List<Execution> children = repo.findExecutionsByParentId(parent.getId());
            for (Execution child : children) {
                parent.setCurrentNodeId(child.getCurrentNodeId());
                child.setStatus(ExecutionStatus.COMPLETED);
                repo.updateExecution(child);
            }
        } else {
            parent.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(parent);
        }
        return true;
    }

    private boolean handleJoin(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        if (!exec.isChild()) {
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (!outgoing.isEmpty()) {
                exec.setCurrentNodeId(outgoing.get(0).getTo());
            }
            return true;
        }

        InstanceRepository repo = context.getInstanceRepository();
        List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());

        boolean allArrived = siblings.stream().allMatch(sibling ->
                sibling.getId().equals(exec.getId()) ||
                        sibling.isCompleted() ||
                        sibling.getCurrentNodeId().equals(node.getId()));

        if (allArrived) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null && parent.isWaiting()) {
                parent.setStatus(ExecutionStatus.ACTIVE);
                List<Transition> afterJoin = context.getDefinition().getOutgoingTransitions(node.getId());
                if (!afterJoin.isEmpty()) {
                    parent.setCurrentNodeId(afterJoin.get(0).getTo());
                }
                repo.updateExecution(parent);
            }
            for (Execution s : siblings) {
                if (!s.isCompleted()) {
                    s.setStatus(ExecutionStatus.COMPLETED);
                    repo.updateExecution(s);
                }
            }
        } else {
            exec.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(exec);
        }
        return true;
    }

    private boolean evaluateCondition(Condition condition, Map<String, Object> variables,
                                       ExecutionContext context) {
        if (condition.getType() == ConditionType.EXPRESSION) {
            return context.getExpressionEvaluator().evaluateToBoolean(condition.getExpr(), variables);
        } else {
            try {
                Class<?> clazz = Class.forName(condition.getClassName());
                ConditionEvaluator evaluator = (ConditionEvaluator) clazz.getDeclaredConstructor().newInstance();
                return evaluator.evaluate(variables);
            } catch (Exception e) {
                throw new RuntimeException("Cannot evaluate condition: " + condition.getClassName(), e);
            }
        }
    }
}

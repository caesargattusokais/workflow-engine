package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.ext.ConditionEvaluator;
import com.github.wf.model.*;
import java.util.List;
import java.util.Map;

public class ExclusiveGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        Map<String, Object> variables = context.getVariables();

        for (Transition t : outgoing) {
            if (t.isConditional()) {
                if (evaluateCondition(t.getCondition(), variables, context)) {
                    context.getExecution().setCurrentNodeId(t.getTo());
                    return true;
                }
            } else if (t.isDefault()) {
                context.getExecution().setCurrentNodeId(t.getTo());
                return true;
            }
        }

        throw new IllegalStateException("No outgoing transition matched for exclusive gateway: " + node.getId());
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

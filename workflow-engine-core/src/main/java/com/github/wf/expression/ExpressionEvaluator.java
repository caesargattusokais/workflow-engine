package com.github.wf.expression;

import java.util.Map;

public interface ExpressionEvaluator {
    Object evaluate(String expression, Map<String, Object> variables);

    default boolean evaluateToBoolean(String expression, Map<String, Object> variables) {
        Object result = evaluate(expression, variables);
        if (result instanceof Boolean) return (Boolean) result;
        return false;
    }
}

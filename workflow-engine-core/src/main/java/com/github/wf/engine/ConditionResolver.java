package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.ext.ConditionEvaluator;
import com.github.wf.model.Condition;
import com.github.wf.model.ConditionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates a {@link Condition} using either a SpEL expression or a Java
 * {@link ConditionEvaluator} class. Single definition of the "SpEL or Java
 * class" dispatch that used to be copy-pasted into every gateway/task runner.
 *
 * <p>Loaded evaluator classes are cached in a static map (shared across
 * engines and runners); instantiation per evaluation is unavoidable because
 * evaluators may carry per-evaluation state.</p>
 */
public final class ConditionResolver {

    private static final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    private ConditionResolver() {}

    /**
     * Evaluate a condition. Null conditions evaluate to {@code false}
     * (matches the historical behavior at all call sites).
     */
    public static boolean evaluate(Condition condition, Map<String, Object> variables,
                                   ExpressionEvaluator expressionEvaluator) {
        if (condition == null) return false;
        if (condition.getType() == ConditionType.EXPRESSION) {
            return expressionEvaluator.evaluateToBoolean(condition.getExpr(), variables);
        }
        String className = condition.getClassName();
        try {
            Class<?> clazz = CLASS_CACHE.computeIfAbsent(className, ConditionResolver::loadClass);
            ConditionEvaluator evaluator = (ConditionEvaluator) clazz.getDeclaredConstructor().newInstance();
            return evaluator.evaluate(variables);
        } catch (Exception e) {
            throw new RuntimeException("Cannot evaluate condition: " + className, e);
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Condition class not found: " + name, e);
        }
    }
}

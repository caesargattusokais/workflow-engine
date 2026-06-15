package com.github.wf.model;

import java.util.Objects;

public class Condition {
    private final ConditionType type;
    private final String expr;
    private final String className;

    private Condition(ConditionType type, String expr, String className) {
        this.type = Objects.requireNonNull(type);
        this.expr = expr;
        this.className = className;
    }

    public static Condition expression(String expr) {
        return new Condition(ConditionType.EXPRESSION, Objects.requireNonNull(expr), null);
    }

    public static Condition javaClass(String className) {
        return new Condition(ConditionType.JAVA_CLASS, null, Objects.requireNonNull(className));
    }

    public ConditionType getType() { return type; }
    public String getExpr() { return expr; }
    public String getClassName() { return className; }
}

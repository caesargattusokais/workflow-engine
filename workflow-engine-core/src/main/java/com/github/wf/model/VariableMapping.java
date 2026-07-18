package com.github.wf.model;

import java.util.Objects;

public class VariableMapping {
    private final String from;
    private final String to;
    private final String expr;  // optional SpEL expression for value transformation

    public VariableMapping(String from, String to, String expr) {
        this.from = Objects.requireNonNull(from, "from must not be null");
        this.to = to != null ? to : from;
        this.expr = expr;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getExpr() { return expr; }
}

package com.github.wf.model;

import java.util.Objects;

public class RoutingRule {
    public enum RoutingType { CONDITIONAL, DEFAULT }

    private final RoutingType type;
    private final Condition condition;
    private final String to;

    private RoutingRule(RoutingType type, Condition condition, String to) {
        this.type = Objects.requireNonNull(type);
        this.condition = condition;
        this.to = Objects.requireNonNull(to, "to must not be null");
    }

    public static RoutingRule defaultRule(String to) {
        return new RoutingRule(RoutingType.DEFAULT, null, to);
    }

    public static RoutingRule matched(Condition condition, String to) {
        return new RoutingRule(RoutingType.CONDITIONAL, Objects.requireNonNull(condition), to);
    }

    public RoutingType getType() { return type; }
    public Condition getCondition() { return condition; }
    public String getTo() { return to; }

    public boolean isDefault() { return type == RoutingType.DEFAULT; }
    public boolean isConditional() { return type == RoutingType.CONDITIONAL; }
}

package com.github.wf.model;

import java.util.Objects;
import java.util.UUID;

public class Transition {
    private final String id;
    private final String from;
    private final String to;
    private final TransitionType type;
    private final Condition condition;

    private Transition(String id, String from, String to, TransitionType type, Condition condition) {
        this.id = id != null ? id : UUID.randomUUID().toString().substring(0, 8);
        this.from = Objects.requireNonNull(from);
        this.to = to;
        this.type = type != null ? type : TransitionType.DIRECT;
        this.condition = condition;
    }

    public static Transition direct(String from, String to) {
        return new Transition(null, from, to, TransitionType.DIRECT, null);
    }

    public static Transition conditional(String from, Condition condition) {
        return new Transition(null, from, null, TransitionType.CONDITIONAL, Objects.requireNonNull(condition));
    }

    public static Transition defaultTransition(String from, String to) {
        return new Transition(null, from, to, TransitionType.DEFAULT, null);
    }

    public static Transition result(String from, String to, Condition condition) {
        return new Transition(null, from, to, TransitionType.RESULT, condition);
    }

    public static Transition exception(String from, String to, Condition condition) {
        return new Transition(null, from, to, TransitionType.EXCEPTION, condition);
    }

    public static Transition timeout(String from, String to) {
        return new Transition(null, from, to, TransitionType.TIMEOUT, null);
    }

    public Transition withTo(String to) {
        return new Transition(this.id, this.from, to, this.type, this.condition);
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public TransitionType getType() { return type; }
    public Condition getCondition() { return condition; }
    public boolean isConditional() { return type == TransitionType.CONDITIONAL; }
    public boolean isDefault() { return type == TransitionType.DEFAULT; }
    public boolean isDirect() { return type == TransitionType.DIRECT; }
    public boolean isResult() { return type == TransitionType.RESULT; }
    public boolean isException() { return type == TransitionType.EXCEPTION; }
    public boolean isTimeout() { return type == TransitionType.TIMEOUT; }
}

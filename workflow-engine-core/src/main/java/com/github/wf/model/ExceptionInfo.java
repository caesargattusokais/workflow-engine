package com.github.wf.model;

/** SpEL-accessible POJO for exception context in routing expressions. */
public class ExceptionInfo {
    private final String type;
    private final String message;
    private final String cause;

    public ExceptionInfo(Throwable e) {
        this.type = e.getClass().getName();
        this.message = e.getMessage();
        this.cause = e.getCause() != null ? e.getCause().getClass().getName() : null;
    }

    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getCause() { return cause; }
}

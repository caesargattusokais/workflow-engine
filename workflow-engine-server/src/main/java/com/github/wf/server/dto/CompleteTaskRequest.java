package com.github.wf.server.dto;

import java.util.Map;

public class CompleteTaskRequest {
    private Map<String, Object> variables;
    private String comment;

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> v) { variables = v; }
    public String getComment() { return comment; }
    public void setComment(String c) { comment = c; }
}

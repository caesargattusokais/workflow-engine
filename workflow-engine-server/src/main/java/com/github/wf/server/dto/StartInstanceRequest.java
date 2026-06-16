package com.github.wf.server.dto;

import java.util.Map;

public class StartInstanceRequest {
    private String definitionId;
    private Map<String, Object> variables;

    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String d) { definitionId = d; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> v) { variables = v; }
}

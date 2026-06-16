package com.github.wf.server.dto;

import com.github.wf.model.ProcessInstance;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public class InstanceDetailResponse {
    private String id, definitionId, status;
    private Map<String, Object> variables;
    private Set<String> activeNodeIds;
    private Instant createdAt, completedAt;

    public InstanceDetailResponse(ProcessInstance inst) {
        this.id = inst.getId();
        this.definitionId = inst.getDefinitionId();
        this.status = inst.getStatus().name();
        this.variables = inst.getVariables();
        this.activeNodeIds = inst.getActiveNodeIds();
        this.createdAt = inst.getCreatedAt();
        this.completedAt = inst.getCompletedAt();
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public String getStatus() { return status; }
    public Map<String, Object> getVariables() { return variables; }
    public Set<String> getActiveNodeIds() { return activeNodeIds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}

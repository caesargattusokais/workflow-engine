package com.github.wf.model;

import java.time.Instant;
import java.util.*;

public class ProcessInstance {
    private final String id;
    private final String definitionId;
    private final int definitionVersion;
    private InstanceStatus status;
    private final Map<String, Object> variables;
    private Set<String> activeNodeIds;
    private String parentInstanceId;
    private String parentExecutionId;
    private final Instant createdAt;
    private Instant completedAt;

    public ProcessInstance(String id, String definitionId, Map<String, Object> variables) {
        this(id, definitionId, 0, variables, (String) null, (String) null);
    }

    public ProcessInstance(String id, String definitionId, int definitionVersion, Map<String, Object> variables) {
        this(id, definitionId, definitionVersion, variables, null, null, Instant.now(), null);
    }

    public ProcessInstance(String id, String definitionId, int definitionVersion,
                           Map<String, Object> variables,
                           String parentInstanceId, String parentExecutionId) {
        this(id, definitionId, definitionVersion, variables, parentInstanceId, parentExecutionId,
             Instant.now(), null);
    }

    public ProcessInstance(String id, String definitionId, int definitionVersion,
                           Map<String, Object> variables, Instant createdAt, Instant completedAt) {
        this(id, definitionId, definitionVersion, variables, null, null, createdAt, completedAt);
    }

    public ProcessInstance(String id, String definitionId, int definitionVersion,
                           Map<String, Object> variables,
                           String parentInstanceId, String parentExecutionId,
                           Instant createdAt, Instant completedAt) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.definitionId = Objects.requireNonNull(definitionId);
        this.definitionVersion = definitionVersion;
        this.status = InstanceStatus.RUNNING;
        this.variables = new HashMap<>(variables != null ? variables : Map.of());
        this.activeNodeIds = new HashSet<>();
        this.parentInstanceId = parentInstanceId;
        this.parentExecutionId = parentExecutionId;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public int getDefinitionVersion() { return definitionVersion; }
    public InstanceStatus getStatus() { return status; }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }
    public Set<String> getActiveNodeIds() { return Collections.unmodifiableSet(activeNodeIds); }
    public String getParentInstanceId() { return parentInstanceId; }
    public void setParentInstanceId(String parentInstanceId) { this.parentInstanceId = parentInstanceId; }
    public String getParentExecutionId() { return parentExecutionId; }
    public void setParentExecutionId(String parentExecutionId) { this.parentExecutionId = parentExecutionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(InstanceStatus status) {
        this.status = status;
        if (status == InstanceStatus.COMPLETED || status == InstanceStatus.TERMINATED || status == InstanceStatus.SUSPENDED) {
            this.completedAt = Instant.now();
        }
    }

    public void setVariable(String name, Object value) { this.variables.put(name, value); }
    public void setVariables(Map<String, Object> vars) { this.variables.putAll(vars); }
    public Object getVariable(String name) { return this.variables.get(name); }
    public void removeVariable(String name) { this.variables.remove(name); }
    public void setActiveNodeIds(Set<String> activeNodeIds) { this.activeNodeIds = new HashSet<>(activeNodeIds); }
    public boolean isRunning() { return status == InstanceStatus.RUNNING; }
}

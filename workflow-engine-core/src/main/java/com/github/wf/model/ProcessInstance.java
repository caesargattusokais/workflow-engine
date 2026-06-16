package com.github.wf.model;

import java.time.Instant;
import java.util.*;

public class ProcessInstance {
    private final String id;
    private final String definitionId;
    private InstanceStatus status;
    private final Map<String, Object> variables;
    private Set<String> activeNodeIds;
    private final Instant createdAt;
    private Instant completedAt;

    public ProcessInstance(String id, String definitionId, Map<String, Object> variables) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.definitionId = Objects.requireNonNull(definitionId);
        this.status = InstanceStatus.RUNNING;
        this.variables = new HashMap<>(variables != null ? variables : Map.of());
        this.activeNodeIds = new HashSet<>();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public InstanceStatus getStatus() { return status; }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }
    public Set<String> getActiveNodeIds() { return Collections.unmodifiableSet(activeNodeIds); }
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
    public void setActiveNodeIds(Set<String> activeNodeIds) { this.activeNodeIds = new HashSet<>(activeNodeIds); }
    public boolean isRunning() { return status == InstanceStatus.RUNNING; }
}

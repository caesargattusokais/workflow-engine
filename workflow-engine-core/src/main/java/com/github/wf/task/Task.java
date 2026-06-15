package com.github.wf.task;

import java.time.Instant;
import java.util.*;

public class Task {
    private final String id;
    private final String instanceId;
    private final String nodeId;
    private String assignee;
    private List<String> candidateGroups;
    private TaskStatus status;
    private Map<String, Object> variables;
    private final Instant createdAt;
    private Instant completedAt;

    public Task(String id, String instanceId, String nodeId) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.status = TaskStatus.PENDING;
        this.candidateGroups = new ArrayList<>();
        this.variables = new HashMap<>();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getNodeId() { return nodeId; }
    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public TaskStatus getStatus() { return status; }
    public Map<String, Object> getVariables() { return variables; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setAssignee(String assignee) { this.assignee = assignee; }
    public void setCandidateGroups(List<String> candidateGroups) { this.candidateGroups = candidateGroups; }
    public void setStatus(TaskStatus status) {
        this.status = status;
        if (status != TaskStatus.PENDING) this.completedAt = Instant.now();
    }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    public boolean isPending() { return status == TaskStatus.PENDING; }
}

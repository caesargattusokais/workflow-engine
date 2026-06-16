package com.github.wf.engine;

import com.github.wf.model.ExecutionStatus;
import java.util.Objects;
import java.util.UUID;

public class Execution {
    private final String id;
    private final String instanceId;
    private String currentNodeId;
    private final String parentExecutionId;
    private ExecutionStatus status;
    private int retryAttempt = 0;
    private long nextRetryAt = 0;    // epoch millis; 0 = no pending retry
    private String retryState = null; // null = normal; "SUSPENDED" = suspend instance

    public Execution(String id, String instanceId, String currentNodeId, String parentExecutionId) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.currentNodeId = Objects.requireNonNull(currentNodeId);
        this.parentExecutionId = parentExecutionId;
        this.status = ExecutionStatus.ACTIVE;
        this.retryAttempt = 0;
        this.nextRetryAt = 0;
        this.retryState = null;
    }

    public Execution(String id, String instanceId, String currentNodeId) {
        this(id, instanceId, currentNodeId, null);
    }

    public Execution(String instanceId, String currentNodeId) {
        this(null, instanceId, currentNodeId, null);
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public String getParentExecutionId() { return parentExecutionId; }
    public ExecutionStatus getStatus() { return status; }

    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = Objects.requireNonNull(currentNodeId); }
    public void setStatus(ExecutionStatus status) { this.status = Objects.requireNonNull(status); }

    public boolean isActive() { return status == ExecutionStatus.ACTIVE; }
    public boolean isWaiting() { return status == ExecutionStatus.WAITING; }
    public boolean isCompleted() { return status == ExecutionStatus.COMPLETED; }
    public boolean isChild() { return parentExecutionId != null; }

    public int getRetryAttempt() { return retryAttempt; }
    public void setRetryAttempt(int retryAttempt) { this.retryAttempt = retryAttempt; }
    public long getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(long nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getRetryState() { return retryState; }
    public void setRetryState(String retryState) { this.retryState = retryState; }
}

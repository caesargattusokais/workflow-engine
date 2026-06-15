package com.github.wf.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class HistoricActivity {
    private final String id;
    private final String instanceId;
    private final String nodeId;
    private final String nodeName;
    private final NodeType nodeType;
    private final String executor;
    private final String action;
    private final Instant timestamp;
    private final String comment;

    public HistoricActivity(String id, String instanceId, String nodeId, String nodeName,
                            NodeType nodeType, String executor, String action,
                            Instant timestamp, String comment) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.executor = executor != null ? executor : "system";
        this.action = Objects.requireNonNull(action);
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.comment = comment;
    }

    public static HistoricActivity nodeEnter(String instanceId, String nodeId,
                                              String nodeName, NodeType nodeType) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                "system", "enter", null, null);
    }

    public static HistoricActivity nodeLeave(String instanceId, String nodeId,
                                              String nodeName, NodeType nodeType) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                "system", "leave", null, null);
    }

    public static HistoricActivity taskCompleted(String instanceId, String nodeId,
                                                  String nodeName, NodeType nodeType,
                                                  String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "complete", null, comment);
    }

    public static HistoricActivity taskRejected(String instanceId, String nodeId,
                                                 String nodeName, NodeType nodeType,
                                                 String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "reject", null, comment);
    }

    public static HistoricActivity taskDelegated(String instanceId, String nodeId,
                                                  String nodeName, NodeType nodeType,
                                                  String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "delegate", null, comment);
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getNodeId() { return nodeId; }
    public String getNodeName() { return nodeName; }
    public NodeType getNodeType() { return nodeType; }
    public String getExecutor() { return executor; }
    public String getAction() { return action; }
    public Instant getTimestamp() { return timestamp; }
    public String getComment() { return comment; }
}

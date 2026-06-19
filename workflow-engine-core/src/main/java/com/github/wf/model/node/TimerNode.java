package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class TimerNode extends Node {
    private final String duration;  // ISO 8601 duration e.g. "PT30S", "PT5M", "PT2H"
    private final String deadline;  // ISO 8601 datetime e.g. "2026-06-25T09:00:00Z"

    public TimerNode(String id, String name, String duration, String deadline, List<String> listeners) {
        super(id, name, NodeType.TIMER, listeners);
        this.duration = duration;
        this.deadline = deadline;
    }
    public TimerNode(String id, String name, String duration, String deadline) {
        this(id, name, duration, deadline, null);
    }
    public String getDuration() { return duration; }
    public String getDeadline() { return deadline; }
}

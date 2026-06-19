package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class UserTask extends Node {
    private final String assignee;
    private final List<String> candidateGroups;
    private final String dynamicRouter;
    private final String boundaryTimer; // ISO 8601 duration, e.g. "PT30M"

    public UserTask(String id, String name, String assignee,
                    List<String> candidateGroups, String dynamicRouter,
                    String boundaryTimer,
                    List<String> listeners) {
        super(id, name, NodeType.USER_TASK, listeners);
        this.assignee = assignee;
        this.candidateGroups = candidateGroups != null ? candidateGroups : List.of();
        this.dynamicRouter = dynamicRouter;
        this.boundaryTimer = boundaryTimer;
    }

    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public String getDynamicRouter() { return dynamicRouter; }
    public String getBoundaryTimer() { return boundaryTimer; }
}

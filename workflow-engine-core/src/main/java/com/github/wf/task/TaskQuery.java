package com.github.wf.task;

import java.util.ArrayList;
import java.util.List;

public class TaskQuery {
    private String assignee;
    private List<String> candidateGroups = new ArrayList<>();
    private String instanceId;
    private TaskStatus status;

    public TaskQuery assignee(String assignee) { this.assignee = assignee; return this; }
    public TaskQuery candidateGroup(String group) { this.candidateGroups.add(group); return this; }
    public TaskQuery candidateGroups(List<String> groups) { this.candidateGroups.addAll(groups); return this; }
    public TaskQuery instanceId(String instanceId) { this.instanceId = instanceId; return this; }
    public TaskQuery status(TaskStatus status) { this.status = status; return this; }

    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public String getInstanceId() { return instanceId; }
    public TaskStatus getStatus() { return status; }

    public boolean matches(Task task) {
        if (assignee != null && !assignee.equals(task.getAssignee())) return false;
        if (instanceId != null && !instanceId.equals(task.getInstanceId())) return false;
        if (status != null && status != task.getStatus()) return false;
        if (!candidateGroups.isEmpty()) {
            boolean hasGroup = candidateGroups.stream()
                    .anyMatch(g -> task.getCandidateGroups().contains(g));
            if (!hasGroup) return false;
        }
        return true;
    }
}

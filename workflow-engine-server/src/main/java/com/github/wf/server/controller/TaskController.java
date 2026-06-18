package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.server.dto.CompleteTaskRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final WorkflowEngine engine;

    public TaskController(WorkflowEngine engine) { this.engine = engine; }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String candidateGroup,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String status) {

        var q = engine.taskQuery();
        if (assignee != null) q.assignee(assignee);
        if (candidateGroup != null) q.candidateGroup(candidateGroup);
        if (instanceId != null) q.instanceId(instanceId);
        if (status != null) q.status(com.github.wf.task.TaskStatus.valueOf(status));

        return engine.queryTasks(q).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("instanceId", t.getInstanceId());
            m.put("nodeId", t.getNodeId());
            m.put("assignee", t.getAssignee());
            m.put("status", t.getStatus().name());
            m.put("candidateGroups", t.getCandidateGroups());
            m.put("variables", t.getVariables());
            m.put("createdAt", t.getCreatedAt());
            return m;
        }).toList();
    }

    @PostMapping("/{id}/complete")
    public void complete(@PathVariable("id") String id, @RequestBody CompleteTaskRequest req) {
        engine.completeTask(id, req.getVariables(), req.getComment());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        engine.rejectTask(id, body.getOrDefault("comment", ""));
    }

    @PostMapping("/{id}/delegate")
    public void delegate(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        engine.delegateTask(id, body.get("newAssignee"));
    }
}

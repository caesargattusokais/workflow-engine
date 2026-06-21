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
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "candidateGroup", required = false) String candidateGroup,
            @RequestParam(value = "instanceId", required = false) String instanceId,
            @RequestParam(value = "status", required = false) String status) {

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

    /** Feishu card callback — browser GET */
    @GetMapping("/{id}/complete")
    public String completeGet(@PathVariable("id") String id,
            @RequestParam(value = "comment", defaultValue = "飞书审批通过") String comment) {
        engine.completeTask(id, Map.of("source", "feishu"), comment);
        return "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><h2 style='color:green'>✓ 已通过</h2><p>审批完成</p></body></html>";
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        engine.rejectTask(id, body.getOrDefault("comment", ""));
    }

    /** Feishu card callback — browser GET */
    @GetMapping("/{id}/reject")
    public String rejectGet(@PathVariable("id") String id,
            @RequestParam(value = "comment", defaultValue = "飞书驳回") String comment) {
        engine.rejectTask(id, comment);
        return "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><h2 style='color:red'>✗ 已驳回</h2><p>任务已驳回</p></body></html>";
    }

    @PostMapping("/{id}/delegate")
    public void delegate(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        engine.delegateTask(id, body.get("newAssignee"));
    }
}

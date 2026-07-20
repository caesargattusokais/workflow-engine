package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.server.dto.CompleteTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
@Tag(name = "Tasks", description = "User task operations — list, complete, reject, and delegate pending tasks")
public class TaskController {

    private final WorkflowEngine engine;

    public TaskController(WorkflowEngine engine) { this.engine = engine; }

    @GetMapping
    @Operation(summary = "List tasks", description = "Query user tasks with optional filters for assignee, candidate group, instance, and status. Defaults to PENDING when no status is specified.")
    @ApiResponse(responseCode = "200", description = "List of matching tasks",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public List<Map<String, Object>> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Filter by assignee user ID")
            @RequestParam(value = "assignee", required = false) String assignee,
            @Parameter(description = "Filter by candidate group name")
            @RequestParam(value = "candidateGroup", required = false) String candidateGroup,
            @Parameter(description = "Filter by process instance ID")
            @RequestParam(value = "instanceId", required = false) String instanceId,
            @Parameter(description = "Filter by task status (PENDING, COMPLETED, REJECTED)")
            @RequestParam(value = "status", required = false) String status) {

        var q = engine.taskQuery();
        if (assignee != null) q.assignee(assignee);
        if (candidateGroup != null) q.candidateGroup(candidateGroup);
        if (instanceId != null) q.instanceId(instanceId);
        // Default to PENDING only if no status filter specified
        if (status != null) {
            q.status(com.github.wf.task.TaskStatus.valueOf(status));
        } else {
            q.status(com.github.wf.task.TaskStatus.PENDING);
        }

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
    @Operation(summary = "Complete a task", description = "Complete a pending user task, optionally passing variables and a comment")
    @ApiResponse(responseCode = "200", description = "Task completed")
    @ApiResponse(responseCode = "404", description = "Task not found")
    public void complete(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Task ID") @PathVariable("id") String id,
            @RequestBody CompleteTaskRequest req) {
        engine.completeTask(id, req.getVariables(), req.getComment());
    }

    /** Feishu card callback — browser GET */
    @GetMapping("/{id}/complete")
    @Operation(summary = "Complete a task (Feishu callback)", description = "Browser-friendly GET endpoint for completing a task via Feishu card callback. Returns an HTML confirmation page.")
    @ApiResponse(responseCode = "200", description = "HTML confirmation page",
            content = @Content(mediaType = "text/html"))
    @ApiResponse(responseCode = "404", description = "Task not found")
    public String completeGet(
            @Parameter(description = "Task ID") @PathVariable("id") String id,
            @Parameter(description = "Completion comment")
            @RequestParam(value = "comment", defaultValue = "飞书审批通过") String comment) {
        engine.completeTask(id, Map.of("source", "feishu"), comment);
        return "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><h2 style='color:green'>✓ 已通过</h2><p>审批完成</p></body></html>";
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a task", description = "Reject a pending user task with an optional comment")
    @ApiResponse(responseCode = "200", description = "Task rejected")
    @ApiResponse(responseCode = "404", description = "Task not found")
    public void reject(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Task ID") @PathVariable("id") String id,
            @RequestBody Map<String, String> body) {
        engine.rejectTask(id, body.getOrDefault("comment", ""));
    }

    /** Feishu card callback — browser GET */
    @GetMapping("/{id}/reject")
    @Operation(summary = "Reject a task (Feishu callback)", description = "Browser-friendly GET endpoint for rejecting a task via Feishu card callback. Returns an HTML confirmation page.")
    @ApiResponse(responseCode = "200", description = "HTML confirmation page",
            content = @Content(mediaType = "text/html"))
    @ApiResponse(responseCode = "404", description = "Task not found")
    public String rejectGet(
            @Parameter(description = "Task ID") @PathVariable("id") String id,
            @Parameter(description = "Rejection comment")
            @RequestParam(value = "comment", defaultValue = "飞书驳回") String comment) {
        engine.rejectTask(id, comment);
        return "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><h2 style='color:red'>✗ 已驳回</h2><p>任务已驳回</p></body></html>";
    }

    @PostMapping("/{id}/delegate")
    @Operation(summary = "Delegate a task", description = "Delegate a pending task to a different assignee")
    @ApiResponse(responseCode = "200", description = "Task delegated")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @ApiResponse(responseCode = "400", description = "Missing newAssignee in request body")
    public void delegate(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Task ID") @PathVariable("id") String id,
            @RequestBody Map<String, String> body) {
        engine.delegateTask(id, body.get("newAssignee"));
    }
}

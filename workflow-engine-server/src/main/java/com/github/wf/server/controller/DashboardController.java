package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
@Tag(name = "Dashboard", description = "Monitoring dashboard — instance statistics and execution timeline")
public class DashboardController {

    private final WorkflowEngine engine;

    public DashboardController(WorkflowEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get instance statistics", description = "Return aggregated instance statistics (running, completed, suspended, etc.) with pending task count. Optionally filter by a specific definition.")
    @ApiResponse(responseCode = "200", description = "Statistics map",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> stats(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Filter stats by process definition ID")
            @RequestParam(value = "definitionId", required = false) String definitionId) {
        var groups = com.github.wf.server.web.GroupScope.groups(request);
        Map<String, Object> result = engine.instanceRepository
                .getVisibleStats(userId, groups, definitionId == null || definitionId.isEmpty() ? null : definitionId)
                .toMap();
        // Pending tasks — instances visible to this user (owned or shared via group)
        var visibleIds = engine.instanceRepository.findVisible(userId, groups, null).stream()
                .map(i -> i.getId()).collect(java.util.stream.Collectors.toSet());
        long pendingTasks = engine.queryTasks(
                new com.github.wf.task.TaskQuery().status(com.github.wf.task.TaskStatus.PENDING)).stream()
                .filter(t -> visibleIds.contains(t.getInstanceId()))
                .count();
        result.put("pendingTasks", pendingTasks);
        return result;
    }

    private String ownerOf(String instanceId) {
        var inst = engine.instanceRepository.findById(instanceId);
        return inst == null ? null : (String) inst.getVariable("_userId");
    }

    @GetMapping("/timeline/{instanceId}")
    @Operation(summary = "Get instance timeline", description = "Return the execution timeline for a process instance — each step with node info, action, timestamp, and duration to next step")
    @ApiResponse(responseCode = "200", description = "Timeline entries",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public List<Map<String, Object>> timeline(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Process instance ID") @PathVariable("instanceId") String instanceId) {
        com.github.wf.server.web.TenantGuard.requireOwnedInstance(
                engine, userId, com.github.wf.server.web.GroupScope.groups(request), instanceId);
        List<HistoricActivity> history = engine.history(instanceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            HistoricActivity h = history.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", h.getNodeId());
            m.put("nodeName", h.getNodeName());
            m.put("action", h.getAction());
            m.put("time", h.getTimestamp().toString());
            if (i + 1 < history.size()) {
                long duration = history.get(i + 1).getTimestamp().toEpochMilli()
                        - h.getTimestamp().toEpochMilli();
                m.put("durationMs", duration);
            }
            result.add(m);
        }
        return result;
    }
}

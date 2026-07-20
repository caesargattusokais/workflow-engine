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
            @Parameter(description = "Filter stats by process definition ID")
            @RequestParam(value = "definitionId", required = false) String definitionId) {
        Map<String, Object> result;
        if (definitionId != null && !definitionId.isEmpty()) {
            result = engine.instanceRepository.getStatsByDefinition(definitionId).toMap();
        } else {
            result = engine.instanceRepository.getStats().toMap();
        }
        // Add pending task count
        long pendingTasks = engine.queryTasks(
                new com.github.wf.task.TaskQuery().status(com.github.wf.task.TaskStatus.PENDING)).size();
        result.put("pendingTasks", pendingTasks);
        return result;
    }

    @GetMapping("/timeline/{instanceId}")
    @Operation(summary = "Get instance timeline", description = "Return the execution timeline for a process instance — each step with node info, action, timestamp, and duration to next step")
    @ApiResponse(responseCode = "200", description = "Timeline entries",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public List<Map<String, Object>> timeline(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Process instance ID") @PathVariable("instanceId") String instanceId) {
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

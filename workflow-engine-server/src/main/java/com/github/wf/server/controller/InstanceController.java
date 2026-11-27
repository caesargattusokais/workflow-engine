package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import com.github.wf.server.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/instances")
@CrossOrigin(origins = "*")
@Tag(name = "Instances", description = "Process instance lifecycle — start, monitor, resume, terminate, and delete running/completed instances")
public class InstanceController {

    private final WorkflowEngine engine;

    public InstanceController(WorkflowEngine engine) { this.engine = engine; }

    @PostMapping
    @Operation(summary = "Start an instance", description = "Start a new process instance from a deployed definition, with optional variables")
    @ApiResponse(responseCode = "200", description = "Instance started",
            content = @Content(schema = @Schema(implementation = InstanceDetailResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid definition ID or variables")
    public InstanceDetailResponse start(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody StartInstanceRequest req) {
        Map<String, Object> vars = req.getVariables() != null
                ? new HashMap<>(req.getVariables()) : new HashMap<>();
        vars.put("_userId", userId);
        String group = com.github.wf.server.web.GroupScope.currentGroup(request);
        if (group != null) vars.put(com.github.wf.engine.InternalVars.GROUP_KEY, group);
        ProcessInstance inst = engine.start(req.getDefinitionId(), vars);
        return new InstanceDetailResponse(inst);
    }

    /** Backward-compatible overloads for in-process callers/tests (no HTTP scope). */
    public InstanceDetailResponse start(String userId, StartInstanceRequest req) { return start(userId, null, req); }
    public InstanceDetailResponse get(String userId, String id) { return get(userId, null, id); }

    @GetMapping
    @Operation(summary = "List instances", description = "Return a paginated list of process instances, optionally filtered by status and definition ID")
    @ApiResponse(responseCode = "200", description = "Paginated instance list",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Filter by instance status (e.g. RUNNING, COMPLETED, SUSPENDED)")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by process definition ID")
            @RequestParam(value = "definitionId", required = false) String definitionId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "50") int size) {
        var groups = com.github.wf.server.web.GroupScope.groups(request);
        // Filter by owner OR group in the query itself BEFORE pagination.
        java.util.List<com.github.wf.model.ProcessInstance> all =
                engine.instanceRepository.findVisiblePaginated(userId, groups, definitionId, page, size, status);
        long total = engine.instanceRepository.countVisible(userId, groups, definitionId, status);
        var filtered = all.stream()
                .map(InstanceDetailResponse::new)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", filtered);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        return result;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get instance detail", description = "Retrieve full details of a process instance by ID")
    @ApiResponse(responseCode = "200", description = "Instance details",
            content = @Content(schema = @Schema(implementation = InstanceDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public InstanceDetailResponse get(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        ProcessInstance inst = com.github.wf.server.web.TenantGuard.requireOwnedInstance(
                engine, userId, com.github.wf.server.web.GroupScope.groups(request), id);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping("/summary")
    @Operation(summary = "Instance summary", description = "Return aggregated instance counts grouped by definition and status")
    @ApiResponse(responseCode = "200", description = "Summary map of definition → status → count",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Map<String, Long>> summary(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request) {
        return engine.instanceRepository.getVisibleSummary(
                userId, com.github.wf.server.web.GroupScope.groups(request));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get instance history", description = "Return the ordered list of historic activities for a process instance")
    @ApiResponse(responseCode = "200", description = "List of historic activities",
            content = @Content(schema = @Schema(implementation = HistoricActivity.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public List<HistoricActivity> history(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        com.github.wf.server.web.TenantGuard.requireOwnedInstance(
                engine, userId, com.github.wf.server.web.GroupScope.groups(request), id);
        return engine.history(id);
    }

    @PostMapping("/recover")
    @Operation(summary = "Trigger recovery", description = "Manually trigger the engine recovery process to resume WAITING/TIMER_PENDING/RETRY_PENDING executions after a restart")
    @ApiResponse(responseCode = "200", description = "Recovery triggered",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> recover() {
        engine.recover();
        return Map.of("status", "ok", "message", "Recovery triggered — check logs for details");
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a suspended instance", description = "Resume a SUSPENDED process instance so execution can continue")
    @ApiResponse(responseCode = "200", description = "Instance resumed",
            content = @Content(schema = @Schema(implementation = InstanceDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public InstanceDetailResponse resume(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        com.github.wf.server.web.TenantGuard.requireAdminInstance(
                engine, userId, com.github.wf.server.web.GroupScope.adminGroups(request), id);
        engine.resume(id);
        return new InstanceDetailResponse(engine.instanceRepository.findById(id));
    }

    @PostMapping("/{id}/terminate")
    @Operation(summary = "Terminate an instance", description = "Force-terminate a running process instance with an optional reason")
    @ApiResponse(responseCode = "200", description = "Instance terminated")
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public void terminate(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Instance ID") @PathVariable("id") String id,
            @RequestBody Map<String, String> body) {
        com.github.wf.server.web.TenantGuard.requireAdminInstance(
                engine, userId, com.github.wf.server.web.GroupScope.adminGroups(request), id);
        engine.terminate(id, body.getOrDefault("reason", "terminated by user"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an instance", description = "Permanently delete a completed or terminated process instance (cannot delete running instances)")
    @ApiResponse(responseCode = "200", description = "Instance deleted")
    @ApiResponse(responseCode = "404", description = "Instance not found")
    @ApiResponse(responseCode = "400", description = "Cannot delete a running instance")
    public void delete(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        var inst = com.github.wf.server.web.TenantGuard.requireAdminInstance(
                engine, userId, com.github.wf.server.web.GroupScope.adminGroups(request), id);
        if (inst.isRunning()) throw new IllegalStateException("Cannot delete running instance");
        engine.instanceRepository.deleteById(id);
    }
}

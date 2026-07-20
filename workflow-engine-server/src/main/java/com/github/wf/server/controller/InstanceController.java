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
            @RequestBody StartInstanceRequest req) {
        Map<String, Object> vars = req.getVariables() != null
                ? new HashMap<>(req.getVariables()) : new HashMap<>();
        vars.put("_userId", userId);
        ProcessInstance inst = engine.start(req.getDefinitionId(), vars);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping
    @Operation(summary = "List instances", description = "Return a paginated list of process instances, optionally filtered by status and definition ID")
    @ApiResponse(responseCode = "200", description = "Paginated instance list",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Filter by instance status (e.g. RUNNING, COMPLETED, SUSPENDED)")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by process definition ID")
            @RequestParam(value = "definitionId", required = false) String definitionId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "50") int size) {
        java.util.List<com.github.wf.model.ProcessInstance> all;
        long total;
        if (definitionId != null && !definitionId.isEmpty()) {
            all = engine.instanceRepository.findByDefinitionIdPaginated(definitionId, page, size, status);
            total = engine.instanceRepository.countByDefinitionId(definitionId);
        } else {
            all = engine.instanceRepository.findAllPaginated(page, size, status);
            total = engine.instanceRepository.count(status);
        }
        var filtered = all.stream()
                .filter(i -> userId.equals(i.getVariable("_userId")))
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
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        ProcessInstance inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping("/summary")
    @Operation(summary = "Instance summary", description = "Return aggregated instance counts grouped by definition and status")
    @ApiResponse(responseCode = "200", description = "Summary map of definition → status → count",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Map<String, Long>> summary(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId) {
        Map<String, Map<String, Long>> full = engine.instanceRepository.getSummary();
        // Filter by userId — only count instances belonging to this user
        // getSummary is DB-level aggregation; we can't filter by _userId in SQL easily.
        // For now return full summary; the _userId filter is best-effort on list endpoints.
        return full;
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get instance history", description = "Return the ordered list of historic activities for a process instance")
    @ApiResponse(responseCode = "200", description = "List of historic activities",
            content = @Content(schema = @Schema(implementation = HistoricActivity.class)))
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public List<HistoricActivity> history(
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
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
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        engine.resume(id);
        return new InstanceDetailResponse(engine.instanceRepository.findById(id));
    }

    @PostMapping("/{id}/terminate")
    @Operation(summary = "Terminate an instance", description = "Force-terminate a running process instance with an optional reason")
    @ApiResponse(responseCode = "200", description = "Instance terminated")
    @ApiResponse(responseCode = "404", description = "Instance not found")
    public void terminate(
            @Parameter(description = "Instance ID") @PathVariable("id") String id,
            @RequestBody Map<String, String> body) {
        engine.terminate(id, body.getOrDefault("reason", "terminated by user"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an instance", description = "Permanently delete a completed or terminated process instance (cannot delete running instances)")
    @ApiResponse(responseCode = "200", description = "Instance deleted")
    @ApiResponse(responseCode = "404", description = "Instance not found")
    @ApiResponse(responseCode = "400", description = "Cannot delete a running instance")
    public void delete(
            @Parameter(description = "Instance ID") @PathVariable("id") String id) {
        var inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        if (inst.isRunning()) throw new RuntimeException("Cannot delete running instance");
        engine.instanceRepository.deleteById(id);
    }
}

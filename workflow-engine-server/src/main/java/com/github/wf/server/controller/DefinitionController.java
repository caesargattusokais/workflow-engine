package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.DefinitionRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.server.dto.DeployRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/definitions")
@CrossOrigin(origins = "*")
@Tag(name = "Definitions", description = "Process definition management — deploy YAML definitions, list versions, retrieve graph layout, and delete definitions")
public class DefinitionController {

    private final WorkflowEngine engine;
    private final DefinitionRepository repo;

    public DefinitionController(WorkflowEngine engine, DefinitionRepository repo) {
        this.engine = engine;
        this.repo = repo;
        engine.setProcessParser(new YamlProcessParser());
    }

    @PostMapping
    @Operation(summary = "Deploy a definition", description = "Deploy a process definition from YAML. The YAML is parsed, validated, and stored. Canvas positions can be included for the visual designer.")
    @ApiResponse(responseCode = "200", description = "Deployed process definition",
            content = @Content(schema = @Schema(implementation = ProcessDefinition.class)))
    @ApiResponse(responseCode = "400", description = "Invalid YAML syntax or validation error")
    public ProcessDefinition deploy(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        repo.save(userId, com.github.wf.server.web.GroupScope.currentGroup(request), def, req.getPositions());
        return def;
    }

    /** Backward-compatible overloads for in-process callers/tests (no HTTP scope). */
    public ProcessDefinition deploy(String userId, DeployRequest req) { return deploy(userId, null, req); }
    public GraphResponse graph(String userId, String id, int version) {
        ProcessDefinition def = engine.processRepository.findByIdAndVersion(id, version);
        if (def == null) throw new com.github.wf.server.web.NotFoundException("Not found: " + id);
        return com.github.wf.server.dto.DefinitionGraphMapper.fromDef(def, Map.of());
    }

    @GetMapping
    @Operation(summary = "List definitions", description = "Return a paginated list of the latest version of each process definition belonging to the current user")
    @ApiResponse(responseCode = "200", description = "Paginated definition list",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        var groups = com.github.wf.server.web.GroupScope.groups(request);
        result.put("items", repo.listLatestVisiblePaginated(userId, groups, page, size));
        result.put("page", page);
        result.put("size", size);
        result.put("total", repo.countVisible(userId, groups));
        return result;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a definition", description = "Retrieve a process definition by ID (latest version)")
    @ApiResponse(responseCode = "200", description = "Process definition",
            content = @Content(schema = @Schema(implementation = ProcessDefinition.class)))
    @ApiResponse(responseCode = "404", description = "Definition not found")
    public ProcessDefinition get(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Process definition ID") @PathVariable("id") String id) {
        ProcessDefinition def = repo.findVisibleByUserAndId(userId,
                com.github.wf.server.web.GroupScope.groups(request), id);
        if (def == null) throw new com.github.wf.server.web.NotFoundException("Not found: " + id);
        return def;
    }

    @GetMapping("/{id}/graph")
    @Operation(summary = "Get definition graph", description = "Return the visual graph representation (nodes + edges with positions) for a process definition. Optionally specify a version.")
    @ApiResponse(responseCode = "200", description = "Graph with nodes and edges",
            content = @Content(schema = @Schema(implementation = GraphResponse.class)))
    @ApiResponse(responseCode = "404", description = "Definition not found")
    public GraphResponse graph(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Process definition ID") @PathVariable("id") String id,
            @Parameter(description = "Specific version (omit for latest)")
            @RequestParam(value = "version", required = false) Integer version) {
        var groups = com.github.wf.server.web.GroupScope.groups(request);
        ProcessDefinition def = repo.findVisibleByUserAndId(userId, groups, id);
        if (def == null) throw new com.github.wf.server.web.NotFoundException("Not found: " + id);
        if (version != null && def.getVersion() != version) {
            throw new com.github.wf.server.web.NotFoundException("Not found: " + id + " v" + version);
        }
        return com.github.wf.server.dto.DefinitionGraphMapper.fromDef(
                def, repo.findVisiblePositions(userId, groups, id, version));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a definition", description = "Permanently delete a process definition and its stored canvas positions")
    @ApiResponse(responseCode = "200", description = "Definition deleted")
    @ApiResponse(responseCode = "404", description = "Definition not found")
    public void delete(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(description = "Process definition ID") @PathVariable("id") String id) {
        var groups = com.github.wf.server.web.GroupScope.groups(request);
        if (repo.findVisibleByUserAndId(userId, groups, id) == null) {
            throw new com.github.wf.server.web.NotFoundException("Not found: " + id);
        }
        repo.delete(userId, id);
    }
}

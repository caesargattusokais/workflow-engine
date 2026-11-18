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
            @RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        repo.save(userId, def, req.getPositions());
        return def;
    }

    @GetMapping
    @Operation(summary = "List definitions", description = "Return a paginated list of the latest version of each process definition belonging to the current user")
    @ApiResponse(responseCode = "200", description = "Paginated definition list",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", repo.listLatestByUserPaginated(userId, page, size));
        result.put("page", page);
        result.put("size", size);
        result.put("total", repo.countByUser(userId));
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
            @Parameter(description = "Process definition ID") @PathVariable("id") String id) {
        ProcessDefinition def = repo.findByUserAndId(userId, id);
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
            @Parameter(description = "Process definition ID") @PathVariable("id") String id,
            @Parameter(description = "Specific version (omit for latest)")
            @RequestParam(value = "version", required = false) Integer version) {
        ProcessDefinition def;
        if (version != null) {
            def = engine.processRepository.findAllVersions(id).stream()
                .filter(d -> d.getVersion() == version).findFirst().orElse(null);
        } else {
            def = engine.processRepository.findLatestById(id);
        }
        if (def == null) def = engine.processRepository.findLatestById(id);
        if (def == null) throw new com.github.wf.server.web.NotFoundException("Not found: " + id + (version != null ? " v" + version : ""));
        return com.github.wf.server.dto.DefinitionGraphMapper.fromDef(def, repo.findPositions(userId, id, version));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a definition", description = "Permanently delete a process definition and its stored canvas positions")
    @ApiResponse(responseCode = "200", description = "Definition deleted")
    @ApiResponse(responseCode = "404", description = "Definition not found")
    public void delete(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Process definition ID") @PathVariable("id") String id) {
        repo.delete(userId, id);
    }
}

package com.github.wf.server.controller;

import com.github.wf.memory.DraftRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
@Tag(name = "Drafts", description = "Workflow draft management — create, edit, copy, and import draft definitions before deployment")
public class DraftController {

    private final DraftRepository repo;

    public DraftController(DraftRepository repo) {
        this.repo = repo;
    }

    private void validateName(String userId, String name, String excludeId) {
        if (name == null || name.isBlank()) throw new RuntimeException("草稿名称不能为空");
        if (repo.nameExists(userId, name, excludeId)) throw new RuntimeException("草稿名称已存在: " + name);
    }

    @GetMapping
    @Operation(summary = "List drafts", description = "Return a paginated list of drafts belonging to the current user")
    @ApiResponse(responseCode = "200", description = "Paginated draft list",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> list(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Page number (1-based)") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(value = "size", defaultValue = "100") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", repo.listByUserPaginated(userId, page, size));
        result.put("page", page);
        result.put("size", size);
        result.put("total", repo.countByUser(userId));
        return result;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a draft", description = "Retrieve a single draft by ID")
    @ApiResponse(responseCode = "200", description = "Draft details",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Draft not found")
    public Map<String, Object> get(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Draft ID") @PathVariable("id") String id) {
        return repo.findById(userId, id);
    }

    @PostMapping
    @Operation(summary = "Create a draft", description = "Create a new empty draft with a given name")
    @ApiResponse(responseCode = "200", description = "Created draft",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "400", description = "Name is blank or already exists")
    public Map<String, Object> create(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Untitled");
        validateName(userId, name, null);
        return repo.create(userId, name);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft", description = "Update draft name, nodes, edges, or version")
    @ApiResponse(responseCode = "200", description = "Updated draft",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Draft not found")
    @ApiResponse(responseCode = "400", description = "Name conflict or invalid data")
    public Map<String, Object> update(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Draft ID") @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        var d = repo.findById(userId, id);
        if (body.containsKey("name")) {
            String name = (String) body.get("name");
            validateName(userId, name, id);
            repo.updateName(userId, id, name);
            d.put("name", name);
        }
        if (body.containsKey("nodes")) { repo.updateNodes(userId, id, body.get("nodes")); d.put("nodes", body.get("nodes")); }
        if (body.containsKey("edges")) { repo.updateEdges(userId, id, body.get("edges")); d.put("edges", body.get("edges")); }
        if (body.containsKey("version")) { repo.updateVersion(userId, id, (int) body.get("version")); d.put("version", body.get("version")); }
        return d;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft", description = "Permanently delete a draft by ID")
    @ApiResponse(responseCode = "200", description = "Draft deleted")
    @ApiResponse(responseCode = "404", description = "Draft not found")
    public void delete(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Draft ID") @PathVariable("id") String id) {
        repo.delete(userId, id);
    }

    @PostMapping("/{id}/copy")
    @Operation(summary = "Copy a draft", description = "Create a copy of an existing draft with an auto-generated name")
    @ApiResponse(responseCode = "200", description = "Copied draft",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Source draft not found")
    public Map<String, Object> copy(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Source draft ID") @PathVariable("id") String id) {
        var original = repo.findById(userId, id);
        String name = original.get("name") + " (Copy)";
        int n = 2;
        while (repo.nameExists(userId, name, null)) name = original.get("name") + " (Copy " + n++ + ")";
        return repo.copy(userId, id, name);
    }

    @PostMapping("/import")
    @Operation(summary = "Import a draft", description = "Import a draft from nodes and edges data (e.g. from a YAML export)")
    @ApiResponse(responseCode = "200", description = "Imported draft",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> importYaml(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Imported");
        String finalName = name;
        int n = 2;
        while (repo.nameExists(userId, finalName, null)) finalName = name + " (" + n++ + ")";
        return repo.importDraft(userId, finalName,
            (List<?>) body.getOrDefault("nodes", List.of()),
            (List<?>) body.getOrDefault("edges", List.of()));
    }
}

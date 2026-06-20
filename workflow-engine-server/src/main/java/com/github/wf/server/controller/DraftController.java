package com.github.wf.server.controller;

import com.github.wf.memory.JdbcDraftRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
public class DraftController {

    private final JdbcDraftRepository repo;

    public DraftController(JdbcDraftRepository repo) {
        this.repo = repo;
    }

    private void validateName(String userId, String name, String excludeId) {
        if (name == null || name.isBlank()) throw new RuntimeException("草稿名称不能为空");
        if (repo.nameExists(userId, name, excludeId)) throw new RuntimeException("草稿名称已存在: " + name);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId) {
        return repo.listByUser(userId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        return repo.findById(userId, id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Untitled");
        validateName(userId, name, null);
        return repo.create(userId, name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader("X-User-Id") String userId,
                                       @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
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
    public void delete(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        repo.delete(userId, id);
    }

    @PostMapping("/{id}/copy")
    public Map<String, Object> copy(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        var original = repo.findById(userId, id);
        String name = original.get("name") + " (Copy)";
        int n = 2;
        while (repo.nameExists(userId, name, null)) name = original.get("name") + " (Copy " + n++ + ")";
        return repo.copy(userId, id, name);
    }

    @PostMapping("/import")
    public Map<String, Object> importYaml(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Imported");
        String finalName = name;
        int n = 2;
        while (repo.nameExists(userId, finalName, null)) finalName = name + " (" + n++ + ")";
        return repo.importDraft(userId, finalName,
            (List<?>) body.getOrDefault("nodes", List.of()),
            (List<?>) body.getOrDefault("edges", List.of()));
    }
}

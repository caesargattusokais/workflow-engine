package com.github.wf.server.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
public class DraftController {

    private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

    private String key(String userId, String id) { return userId + ":" + id; }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId) {
        String prefix = userId + ":";
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@RequestHeader("X-User-Id") String userId,
                                   @PathVariable("id") String id) {
        var d = store.get(key(userId, id));
        if (d == null) throw new RuntimeException("Draft not found: " + id);
        return d;
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader("X-User-Id") String userId,
                                       @RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", id);
        draft.put("name", body.getOrDefault("name", "Untitled"));
        draft.put("nodes", body.getOrDefault("nodes", List.of()));
        draft.put("edges", body.getOrDefault("edges", List.of()));
        draft.put("createdAt", System.currentTimeMillis());
        draft.put("version", 1);
        store.put(key(userId, id), draft);
        return draft;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader("X-User-Id") String userId,
                                       @PathVariable("id") String id,
                                       @RequestBody Map<String, Object> body) {
        var d = store.get(key(userId, id));
        if (d == null) throw new RuntimeException("Draft not found: " + id);
        if (body.containsKey("name")) d.put("name", body.get("name"));
        if (body.containsKey("nodes")) d.put("nodes", body.get("nodes"));
        if (body.containsKey("edges")) d.put("edges", body.get("edges"));
        if (body.containsKey("version")) d.put("version", body.get("version"));
        return d;
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId,
                       @PathVariable("id") String id) {
        store.remove(key(userId, id));
    }

    @PostMapping("/{id}/copy")
    public Map<String, Object> copy(@RequestHeader("X-User-Id") String userId,
                                     @PathVariable("id") String id) {
        var original = store.get(key(userId, id));
        if (original == null) throw new RuntimeException("Draft not found: " + id);
        String newId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> copy = new LinkedHashMap<>(original);
        copy.put("id", newId);
        copy.put("name", original.get("name") + " (Copy)");
        copy.put("version", 1);
        copy.put("createdAt", System.currentTimeMillis());
        store.put(key(userId, newId), copy);
        return copy;
    }

    @PostMapping("/import")
    public Map<String, Object> importYaml(@RequestHeader("X-User-Id") String userId,
                                           @RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", id);
        draft.put("name", body.getOrDefault("name", "Imported"));
        draft.put("nodes", body.getOrDefault("nodes", List.of()));
        draft.put("edges", body.getOrDefault("edges", List.of()));
        draft.put("createdAt", System.currentTimeMillis());
        draft.put("version", 1);
        store.put(key(userId, id), draft);
        return draft;
    }
}

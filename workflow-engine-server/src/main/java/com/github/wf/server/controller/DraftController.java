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
        int currentVersion = d.get("version") instanceof Number ? ((Number)d.get("version")).intValue() : 1;
        d.put("version", currentVersion + 1);
        if (body.containsKey("nodes")) d.put("nodes", body.get("nodes"));
        if (body.containsKey("edges")) d.put("edges", body.get("edges"));
        return d;
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId,
                       @PathVariable("id") String id) {
        store.remove(key(userId, id));
    }
}

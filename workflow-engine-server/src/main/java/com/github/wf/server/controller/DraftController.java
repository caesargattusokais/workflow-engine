package com.github.wf.server.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
public class DraftController {

    private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

    @GetMapping
    public List<Map<String, Object>> list() {
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") String id) {
        var d = store.get(id);
        if (d == null) throw new RuntimeException("Draft not found: " + id);
        return d;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", id);
        draft.put("name", body.getOrDefault("name", "Untitled"));
        draft.put("nodes", body.getOrDefault("nodes", List.of()));
        draft.put("edges", body.getOrDefault("edges", List.of()));
        draft.put("createdAt", System.currentTimeMillis());
        store.put(id, draft);
        return draft;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        var d = store.get(id);
        if (d == null) throw new RuntimeException("Draft not found: " + id);
        if (body.containsKey("name")) d.put("name", body.get("name"));
        if (body.containsKey("nodes")) d.put("nodes", body.get("nodes"));
        if (body.containsKey("edges")) d.put("edges", body.get("edges"));
        return d;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        store.remove(id);
    }
}

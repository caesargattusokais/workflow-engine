package com.github.wf.memory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory draft repository — used with 'memory' profile. Mirrors JdbcDraftRepository API. */
public class InMemoryDraftRepository implements DraftRepository {
    private final List<Map<String, Object>> drafts = new CopyOnWriteArrayList<>();

    public List<Map<String, Object>> listByUser(String userId) {
        return drafts.stream().filter(d -> userId.equals(d.get("_userId"))).toList();
    }
    public Map<String, Object> findById(String userId, String id) {
        return drafts.stream().filter(d -> id.equals(d.get("id")) && userId.equals(d.get("_userId")))
            .findFirst().orElseThrow(() -> new RuntimeException("Draft not found: " + id));
    }
    public Map<String, Object> create(String userId, String name) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", UUID.randomUUID().toString().substring(0, 8));
        d.put("_userId", userId); d.put("name", name);
        d.put("nodes", List.of()); d.put("edges", List.of());
        d.put("version", 1); d.put("createdAt", System.currentTimeMillis());
        drafts.add(d); return d;
    }
    public Map<String, Object> copy(String userId, String originalId, String newName) {
        var orig = findById(userId, originalId);
        Map<String, Object> d = new LinkedHashMap<>(orig);
        d.put("id", UUID.randomUUID().toString().substring(0, 8));
        d.put("name", newName); d.put("version", 1); d.put("createdAt", System.currentTimeMillis());
        drafts.add(d); return d;
    }
    public Map<String, Object> importDraft(String userId, String name, List<?> nodes, List<?> edges) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", UUID.randomUUID().toString().substring(0, 8));
        d.put("_userId", userId); d.put("name", name);
        d.put("nodes", nodes); d.put("edges", edges);
        d.put("version", 1); d.put("createdAt", System.currentTimeMillis());
        drafts.add(d); return d;
    }
    public void updateName(String userId, String id, String name) { findById(userId, id).put("name", name); }
    public void updateNodes(String userId, String id, Object nodes) { findById(userId, id).put("nodes", nodes); }
    public void updateEdges(String userId, String id, Object edges) { findById(userId, id).put("edges", edges); }
    public void updateVersion(String userId, String id, int version) { findById(userId, id).put("version", version); }
    public void delete(String userId, String id) { drafts.removeIf(d -> id.equals(d.get("id"))); }
    public boolean nameExists(String userId, String name, String excludeId) {
        return drafts.stream().anyMatch(d -> userId.equals(d.get("_userId")) && name.equals(d.get("name")) && !d.get("id").equals(excludeId));
    }
}

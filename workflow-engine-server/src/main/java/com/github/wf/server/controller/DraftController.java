package com.github.wf.server.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
public class DraftController {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public DraftController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void validateName(String userId, String name, String excludeId) {
        if (name == null || name.isBlank()) throw new RuntimeException("草稿名称不能为空");
        String sql = excludeId != null
            ? "SELECT COUNT(*) FROM draft WHERE user_id = ? AND name = ? AND id != ?"
            : "SELECT COUNT(*) FROM draft WHERE user_id = ? AND name = ?";
        List<Object> params = new ArrayList<>(List.of(userId, name));
        if (excludeId != null) params.add(excludeId);
        Integer count = jdbc.queryForObject(sql, Integer.class, params.toArray());
        if (count != null && count > 0) throw new RuntimeException("草稿名称已存在: " + name);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId) {
        return jdbc.query(
            "SELECT id, name, nodes_json, edges_json, version, created_at FROM draft WHERE user_id = ? ORDER BY created_at",
            (rs, rowNum) -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", rs.getString("id"));
                d.put("name", rs.getString("name"));
                String nj = rs.getString("nodes_json");
                d.put("nodes", nj != null ? gson.fromJson(nj, new TypeToken<List<Map<String,Object>>>() {}.getType()) : List.of());
                String ej = rs.getString("edges_json");
                d.put("edges", ej != null ? gson.fromJson(ej, new TypeToken<List<Map<String,Object>>>() {}.getType()) : List.of());
                d.put("version", rs.getInt("version"));
                d.put("createdAt", rs.getLong("created_at"));
                return d;
            }, userId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@RequestHeader("X-User-Id") String userId,
                                    @PathVariable("id") String id) {
        List<Map<String, Object>> list = jdbc.query(
            "SELECT id, name, nodes_json, edges_json, version, created_at FROM draft WHERE user_id = ? AND id = ?",
            (rs, rowNum) -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", rs.getString("id"));
                d.put("name", rs.getString("name"));
                String nj = rs.getString("nodes_json");
                d.put("nodes", nj != null ? gson.fromJson(nj, new TypeToken<List<Map<String,Object>>>() {}.getType()) : List.of());
                String ej = rs.getString("edges_json");
                d.put("edges", ej != null ? gson.fromJson(ej, new TypeToken<List<Map<String,Object>>>() {}.getType()) : List.of());
                d.put("version", rs.getInt("version"));
                d.put("createdAt", rs.getLong("created_at"));
                return d;
            }, userId, id);
        if (list.isEmpty()) throw new RuntimeException("Draft not found: " + id);
        return list.get(0);
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader("X-User-Id") String userId,
                                       @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Untitled");
        validateName(userId, name, null);
        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)",
            id, userId, name, "[]", "[]", now);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", id); draft.put("name", name);
        draft.put("nodes", List.of()); draft.put("edges", List.of());
        draft.put("version", 1); draft.put("createdAt", now);
        return draft;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader("X-User-Id") String userId,
                                       @PathVariable("id") String id,
                                       @RequestBody Map<String, Object> body) {
        var existing = get(userId, id); // validates existence
        if (body.containsKey("name")) {
            String newName = (String) body.get("name");
            validateName(userId, newName, id);
            existing.put("name", newName);
            jdbc.update("UPDATE draft SET name = ? WHERE user_id = ? AND id = ?", newName, userId, id);
        }
        if (body.containsKey("nodes")) {
            existing.put("nodes", body.get("nodes"));
            jdbc.update("UPDATE draft SET nodes_json = ? WHERE user_id = ? AND id = ?",
                gson.toJson(body.get("nodes")), userId, id);
        }
        if (body.containsKey("edges")) {
            existing.put("edges", body.get("edges"));
            jdbc.update("UPDATE draft SET edges_json = ? WHERE user_id = ? AND id = ?",
                gson.toJson(body.get("edges")), userId, id);
        }
        if (body.containsKey("version")) {
            existing.put("version", body.get("version"));
            jdbc.update("UPDATE draft SET version = ? WHERE user_id = ? AND id = ?", body.get("version"), userId, id);
        }
        return existing;
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId,
                       @PathVariable("id") String id) {
        jdbc.update("DELETE FROM draft WHERE user_id = ? AND id = ?", userId, id);
    }

    @PostMapping("/{id}/copy")
    public Map<String, Object> copy(@RequestHeader("X-User-Id") String userId,
                                     @PathVariable("id") String id) {
        var original = get(userId, id);
        String newName = original.get("name") + " (Copy)";
        int n = 2;
        while (true) {
            try { validateName(userId, newName, null); break; }
            catch (RuntimeException e) { newName = original.get("name") + " (Copy " + n++ + ")"; }
        }
        String newId = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)",
            newId, userId, newName, gson.toJson(original.get("nodes")), gson.toJson(original.get("edges")), now);
        Map<String, Object> copy = new LinkedHashMap<>(original);
        copy.put("id", newId); copy.put("name", newName);
        copy.put("version", 1); copy.put("createdAt", now);
        return copy;
    }

    @PostMapping("/import")
    public Map<String, Object> importYaml(@RequestHeader("X-User-Id") String userId,
                                           @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Imported");
        String finalName = name;
        int n = 2;
        while (true) {
            try { validateName(userId, finalName, null); break; }
            catch (RuntimeException e) { finalName = name + " (" + n++ + ")"; }
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)",
            id, userId, finalName,
            gson.toJson(body.getOrDefault("nodes", List.of())),
            gson.toJson(body.getOrDefault("edges", List.of())), now);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", id); draft.put("name", finalName);
        draft.put("nodes", body.getOrDefault("nodes", List.of()));
        draft.put("edges", body.getOrDefault("edges", List.of()));
        draft.put("version", 1); draft.put("createdAt", now);
        return draft;
    }
}

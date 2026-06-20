package com.github.wf.memory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * JDBC repository for drafts — used by DraftController.
 */
public class JdbcDraftRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public JdbcDraftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listByUser(String userId) {
        return jdbc.query(
            "SELECT id, name, nodes_json, edges_json, version, created_at FROM draft WHERE user_id = ? ORDER BY created_at",
            (rs, rowNum) -> mapDraft(rs), userId);
    }

    public Map<String, Object> findById(String userId, String id) {
        List<Map<String, Object>> list = jdbc.query(
            "SELECT id, name, nodes_json, edges_json, version, created_at FROM draft WHERE user_id = ? AND id = ?",
            (rs, rowNum) -> mapDraft(rs), userId, id);
        if (list.isEmpty()) throw new RuntimeException("Draft not found: " + id);
        return list.get(0);
    }

    public Map<String, Object> create(String userId, String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, '[]', '[]', 1, ?)",
            id, userId, name, now);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id); d.put("name", name);
        d.put("nodes", List.of()); d.put("edges", List.of());
        d.put("version", 1); d.put("createdAt", now);
        return d;
    }

    public Map<String, Object> copy(String userId, String originalId, String newName) {
        var original = findById(userId, originalId);
        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)",
            id, userId, newName, gson.toJson(original.get("nodes")), gson.toJson(original.get("edges")), now);
        Map<String, Object> d = new LinkedHashMap<>(original);
        d.put("id", id); d.put("name", newName);
        d.put("version", 1); d.put("createdAt", now);
        return d;
    }

    public Map<String, Object> importDraft(String userId, String name, List<?> nodes, List<?> edges) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO draft (id, user_id, name, nodes_json, edges_json, version, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)",
            id, userId, name, gson.toJson(nodes), gson.toJson(edges), now);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id); d.put("name", name);
        d.put("nodes", nodes); d.put("edges", edges);
        d.put("version", 1); d.put("createdAt", now);
        return d;
    }

    public void updateName(String userId, String id, String name) {
        jdbc.update("UPDATE draft SET name = ? WHERE user_id = ? AND id = ?", name, userId, id);
    }

    public void updateNodes(String userId, String id, Object nodes) {
        jdbc.update("UPDATE draft SET nodes_json = ? WHERE user_id = ? AND id = ?", gson.toJson(nodes), userId, id);
    }

    public void updateEdges(String userId, String id, Object edges) {
        jdbc.update("UPDATE draft SET edges_json = ? WHERE user_id = ? AND id = ?", gson.toJson(edges), userId, id);
    }

    public void updateVersion(String userId, String id, int version) {
        jdbc.update("UPDATE draft SET version = ? WHERE user_id = ? AND id = ?", version, userId, id);
    }

    public void delete(String userId, String id) {
        jdbc.update("DELETE FROM draft WHERE user_id = ? AND id = ?", userId, id);
    }

    public boolean nameExists(String userId, String name, String excludeId) {
        String sql = excludeId != null
            ? "SELECT COUNT(*) FROM draft WHERE user_id = ? AND name = ? AND id != ?"
            : "SELECT COUNT(*) FROM draft WHERE user_id = ? AND name = ?";
        List<Object> params = new ArrayList<>(List.of(userId, name));
        if (excludeId != null) params.add(excludeId);
        Integer count = jdbc.queryForObject(sql, Integer.class, params.toArray());
        return count != null && count > 0;
    }

    private Map<String, Object> mapDraft(java.sql.ResultSet rs) throws java.sql.SQLException {
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
    }
}

package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * JDBC repository for definitions metadata — used by DefinitionController.
 */
public class JdbcDefinitionRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public JdbcDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        String posJson = positions != null ? gson.toJson(positions) : null;
        jdbc.update(
            "INSERT INTO definition (id, version, user_id, name, positions_json) VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), positions_json = VALUES(positions_json)",
            def.getId(), def.getVersion(), userId, def.getName(), posJson);
    }

    public List<ProcessDefinition> listLatestByUser(String userId) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        jdbc.query(
            "SELECT id, version, name FROM definition WHERE user_id = ? ORDER BY id, version DESC",
            (rs) -> {
                String id = rs.getString("id");
                latest.putIfAbsent(id, new ProcessDefinition(id, rs.getString("name"),
                    rs.getInt("version"), List.of(), List.of()));
            }, userId);
        return new ArrayList<>(latest.values());
    }

    public ProcessDefinition findByUserAndId(String userId, String id) {
        List<ProcessDefinition> list = jdbc.query(
            "SELECT id, version, name FROM definition WHERE user_id = ? AND id = ? ORDER BY version DESC LIMIT 1",
            (rs, rowNum) -> new ProcessDefinition(rs.getString("id"), rs.getString("name"),
                rs.getInt("version"), List.of(), List.of()),
            userId, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Map<String, Map<String, Double>> findPositions(String userId, String id, Integer version) {
        String sql;
        List<Object> params = new ArrayList<>(List.of(userId, id));
        if (version != null) {
            sql = "SELECT positions_json FROM definition WHERE user_id = ? AND id = ? AND version = ?";
            params.add(version);
        } else {
            sql = "SELECT positions_json FROM definition WHERE user_id = ? AND id = ? ORDER BY version DESC LIMIT 1";
        }
        List<String> list = jdbc.query(sql, (rs, rowNum) -> rs.getString("positions_json"), params.toArray());
        String json = list.isEmpty() ? null : list.get(0);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Map<String, Double>>>() {}.getType());
    }

    public void delete(String userId, String id) {
        jdbc.update("DELETE FROM definition WHERE user_id = ? AND id = ?", userId, id);
    }
}

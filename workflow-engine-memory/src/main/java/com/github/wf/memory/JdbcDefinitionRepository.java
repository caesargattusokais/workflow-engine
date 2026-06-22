package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * JDBC repository for definitions metadata — used by DefinitionController.
 */
public class JdbcDefinitionRepository implements DefinitionRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public JdbcDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        String posJson = positions != null ? gson.toJson(positions) : null;
        int updated = jdbc.update(
            "UPDATE definition SET name = ?, positions_json = ? WHERE user_id = ? AND id = ? AND version = ?",
            def.getName(), posJson, userId, def.getId(), def.getVersion());
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO definition (id, version, user_id, name, positions_json) VALUES (?, ?, ?, ?, ?)",
                def.getId(), def.getVersion(), userId, def.getName(), posJson);
        }
    }

    public List<ProcessDefinition> listLatestByUser(String userId) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        jdbc.query(
            "SELECT d.id, d.version, d.name, p.nodes_json, p.transitions_json " +
            "FROM definition d LEFT JOIN process_definition p ON d.id = p.id AND d.version = p.version " +
            "WHERE d.user_id = ? ORDER BY d.id, d.version DESC",
            (rs) -> {
                String id = rs.getString("id");
                String nodesJson = rs.getString("nodes_json");
                String transJson = rs.getString("transitions_json");
                ProcessDefinition def;
                if (nodesJson != null) {
                    def = JdbcProcessRepository.buildDefStatic(id, rs.getInt("version"),
                        rs.getString("name"), nodesJson, transJson);
                } else {
                    def = new ProcessDefinition(id, rs.getString("name"),
                        rs.getInt("version"), List.of(), List.of());
                }
                latest.putIfAbsent(id, def);
            }, userId);
        return new ArrayList<>(latest.values());
    }

    public ProcessDefinition findByUserAndId(String userId, String id) {
        List<ProcessDefinition> list = jdbc.query(
            "SELECT d.id, d.version, d.name, p.nodes_json, p.transitions_json " +
            "FROM definition d LEFT JOIN process_definition p ON d.id = p.id AND d.version = p.version " +
            "WHERE d.user_id = ? AND d.id = ? ORDER BY d.version DESC LIMIT 1",
            (rs, rowNum) -> {
                String nodesJson = rs.getString("nodes_json");
                if (nodesJson != null) {
                    return JdbcProcessRepository.buildDefStatic(rs.getString("id"),
                        rs.getInt("version"), rs.getString("name"),
                        nodesJson, rs.getString("transitions_json"));
                }
                return new ProcessDefinition(rs.getString("id"), rs.getString("name"),
                    rs.getInt("version"), List.of(), List.of());
            }, userId, id);
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

    @Override
    public List<ProcessDefinition> listLatestByUserPaginated(String userId, int page, int size) {
        // Get latest version per definition with pagination, including nodes/transitions
        return jdbc.query(
            "SELECT d.id, d.version, d.name, p.nodes_json, p.transitions_json FROM definition d " +
            "INNER JOIN (SELECT id, MAX(version) mv FROM definition WHERE user_id = ? GROUP BY id) latest " +
            "ON d.id = latest.id AND d.version = latest.mv AND d.user_id = ? " +
            "LEFT JOIN process_definition p ON d.id = p.id AND d.version = p.version " +
            "ORDER BY d.id LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                String nodesJson = rs.getString("nodes_json");
                if (nodesJson != null) {
                    return JdbcProcessRepository.buildDefStatic(rs.getString("id"),
                        rs.getInt("version"), rs.getString("name"),
                        nodesJson, rs.getString("transitions_json"));
                }
                return new ProcessDefinition(rs.getString("id"), rs.getString("name"),
                    rs.getInt("version"), List.of(), List.of());
            },
            userId, userId, size, (page - 1) * size);
    }

    @Override
    public long countByUser(String userId) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT id) FROM definition WHERE user_id = ?", Long.class, userId);
        return c != null ? c : 0;
    }

    public void delete(String userId, String id) {
        jdbc.update("DELETE FROM definition WHERE user_id = ? AND id = ?", userId, id);
    }
}

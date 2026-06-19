package com.github.wf.memory;

import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed TaskRepository with write-through cache.
 */
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();
    private final Map<String, Task> cache = new ConcurrentHashMap<>();

    public JdbcTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Load existing tasks into cache
        jdbc.query("SELECT * FROM task", (rs) -> {
            Task task = mapTask(rs);
            cache.put(task.getId(), task);
        });
    }

    @Override
    public void save(Task task) {
        cache.put(task.getId(), task);
        writeToDb(task);
    }

    @Override
    public Task findById(String id) {
        return cache.get(id);
    }

    @Override
    public void update(Task task) {
        cache.put(task.getId(), task);
        writeToDb(task);
    }

    @Override
    public List<Task> query(TaskQuery query) {
        return cache.values().stream().filter(query::matches).toList();
    }

    private void writeToDb(Task task) {
        jdbc.update(
            "INSERT INTO task (id, instance_id, node_id, assignee, candidate_groups_json, status, variables_json, created_at, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE assignee=VALUES(assignee), " +
            "candidate_groups_json=VALUES(candidate_groups_json), status=VALUES(status), variables_json=VALUES(variables_json), completed_at=VALUES(completed_at)",
            task.getId(), task.getInstanceId(), task.getNodeId(), task.getAssignee(),
            gson.toJson(task.getCandidateGroups()), task.getStatus().name(),
            gson.toJson(task.getVariables()), task.getCreatedAt().toEpochMilli(),
            task.getCompletedAt() != null ? task.getCompletedAt().toEpochMilli() : null);
    }

    private Task mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        Task task = new Task(rs.getString("id"), rs.getString("instance_id"), rs.getString("node_id"));
        task.setAssignee(rs.getString("assignee"));
        String cgJson = rs.getString("candidate_groups_json");
        if (cgJson != null && !cgJson.isEmpty()) {
            List<String> cg = gson.fromJson(cgJson, new TypeToken<List<String>>() {}.getType());
            task.setCandidateGroups(cg != null ? cg : List.of());
        }
        task.setStatus(TaskStatus.valueOf(rs.getString("status")));
        String varsJson = rs.getString("variables_json");
        if (varsJson != null && !varsJson.isEmpty()) {
            Map<String, Object> vars = gson.fromJson(varsJson, new TypeToken<Map<String, Object>>() {}.getType());
            task.setVariables(vars != null ? vars : Map.of());
        }
        return task;
    }
}

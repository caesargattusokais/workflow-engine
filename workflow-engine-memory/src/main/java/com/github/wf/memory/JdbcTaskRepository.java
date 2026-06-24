package com.github.wf.memory;

import com.github.wf.engine.InstanceLockManager;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JDBC-backed TaskRepository with per-taskId ReadWriteLock (local)
 * and optional distributed lock (redis profile).
 */
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();
    private static final String DIST_LOCK_PREFIX = "wf:lock:task:";
    private final Map<String, ReadWriteLock> locks = new ConcurrentHashMap<>();
    private final InstanceLockManager distLockManager;

    private ReadWriteLock localLock(String taskId) {
        return locks.computeIfAbsent(taskId, k -> new ReentrantReadWriteLock());
    }

    public JdbcTaskRepository(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    public JdbcTaskRepository(JdbcTemplate jdbc, InstanceLockManager distLockManager) {
        this.jdbc = jdbc;
        this.distLockManager = distLockManager;
    }

    @Override
    public void save(Task task) {
        if (distLockManager != null) distLockManager.lock(DIST_LOCK_PREFIX + task.getId());
        var rw = localLock(task.getId());
        rw.writeLock().lock();
        try {
            jdbc.update(
                "INSERT INTO task (id, instance_id, node_id, assignee, candidate_groups_json, status, variables_json, created_at, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                task.getId(), task.getInstanceId(), task.getNodeId(), task.getAssignee(),
                gson.toJson(task.getCandidateGroups()), task.getStatus().name(),
                gson.toJson(task.getVariables()), task.getCreatedAt().toEpochMilli(),
                task.getCompletedAt() != null ? task.getCompletedAt().toEpochMilli() : null);
        } finally {
            rw.writeLock().unlock();
            if (distLockManager != null) distLockManager.unlock(DIST_LOCK_PREFIX + task.getId());
        }
    }

    @Override
    public Task findById(String id) {
        if (distLockManager != null) distLockManager.lock(DIST_LOCK_PREFIX + id);
        var rw = localLock(id);
        rw.readLock().lock();
        try {
            List<Task> list = jdbc.query("SELECT * FROM task WHERE id = ?",
                (rs, rowNum) -> mapTask(rs), id);
            return list.isEmpty() ? null : list.get(0);
        } finally {
            rw.readLock().unlock();
            if (distLockManager != null) distLockManager.unlock(DIST_LOCK_PREFIX + id);
        }
    }

    @Override
    public void update(Task task) {
        if (distLockManager != null) distLockManager.lock(DIST_LOCK_PREFIX + task.getId());
        var rw = localLock(task.getId());
        rw.writeLock().lock();
        try {
            jdbc.update(
                "UPDATE task SET assignee=?, candidate_groups_json=?, status=?, variables_json=?, completed_at=? WHERE id=?",
                task.getAssignee(), gson.toJson(task.getCandidateGroups()), task.getStatus().name(),
                gson.toJson(task.getVariables()),
                task.getCompletedAt() != null ? task.getCompletedAt().toEpochMilli() : null,
                task.getId());
        } finally {
            rw.writeLock().unlock();
            if (distLockManager != null) distLockManager.unlock(DIST_LOCK_PREFIX + task.getId());
        }
    }

    @Override
    public List<Task> query(TaskQuery query) {
        // Read-only scan — no lock needed
        StringBuilder sql = new StringBuilder("SELECT * FROM task WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.getAssignee() != null) { sql.append(" AND assignee = ?"); params.add(query.getAssignee()); }
        if (query.getInstanceId() != null) { sql.append(" AND instance_id = ?"); params.add(query.getInstanceId()); }
        if (query.getStatus() != null) { sql.append(" AND status = ?"); params.add(query.getStatus().name()); }
        if (!query.getCandidateGroups().isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < query.getCandidateGroups().size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("candidate_groups_json LIKE ?");
                params.add("%\"" + query.getCandidateGroups().get(i) + "\"%");
            }
            sql.append(")");
        }

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapTask(rs), params.toArray());
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

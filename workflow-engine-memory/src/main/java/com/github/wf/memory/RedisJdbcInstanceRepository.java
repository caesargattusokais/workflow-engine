package com.github.wf.memory;

import com.github.wf.engine.Execution;

import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import com.google.gson.Gson;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Redis-cached InstanceRepository with DB write-through.
 * Only RUNNING instances and their non-completed executions are cached in Redis.
 * Completed executions are evicted from Redis (kept in DB) — join logic reads
 * siblings from DB via parent_execution_id, so nothing is lost.
 * Non-RUNNING instances are evicted from Redis and served from DB.
 */
public class RedisJdbcInstanceRepository implements InstanceRepository {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Gson gson;

    private static final String INST_PREFIX = "wf:instance:";
    private static final String EXEC_PREFIX = "wf:execution:";
    private static final String EXEC_SET_SUFFIX = ":execs";
    /** Safety-net TTL — a RUNNING instance stuck forever must not leak cache keys. */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    public RedisJdbcInstanceRepository(JdbcTemplate jdbc, StringRedisTemplate redis, Gson gson) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.gson = gson;
    }

    /** Load running instances from Redis/DB — must be called after schema.sql has been executed */
    public void init() {
        loadFromRedis();
    }

    // ── Startup recovery ──

    /** SCAN-based key lookup — KEYS blocks the whole Redis keyspace. */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) keys.add(cursor.next());
        }
        return keys;
    }

    private void loadFromRedis() {
        Set<String> keys = scanKeys(INST_PREFIX + "*");
        if (keys.isEmpty()) {
            // Cold start — load from DB
            loadRunning();
            return;
        }
        for (String key : keys) {
            // Skip execution-set keys
            if (key.endsWith(EXEC_SET_SUFFIX)) continue;
            try {
                String json = redis.opsForValue().get(key);
                if (json == null) continue;
                ProcessInstance inst = gson.fromJson(json, ProcessInstance.class);
                if (inst == null || !inst.isRunning()) continue;
                // Execution JSON already lives in Redis — nothing to load here.
                // The DB is written through on every mutation, so the cold-start
                // DB fallback in findExecutionById is always consistent.
            } catch (Exception e) {
                // Corrupt key — skip
            }
        }
    }

    private void loadRunning() {
        jdbc.query("SELECT * FROM process_instance WHERE status = 'RUNNING'", (rs) -> {
            ProcessInstance inst = mapInstance(rs);
            cacheInstance(inst);
            // Load executions
            jdbc.query("SELECT * FROM execution WHERE instance_id = ?", (rs2) -> {
                Execution exec = mapExecution(rs2);
                cacheExecution(exec);
            }, inst.getId());
        });
    }

    // ── Cache helpers ──

    private void cacheInstance(ProcessInstance inst) {
        redis.opsForValue().set(INST_PREFIX + inst.getId(), gson.toJson(inst), CACHE_TTL);
    }

    private void evictInstance(String instanceId) {
        // Remove all executions
        Set<String> execIds = redis.opsForSet().members(INST_PREFIX + instanceId + EXEC_SET_SUFFIX);
        if (execIds != null) {
            for (String eid : execIds) {
                redis.delete(EXEC_PREFIX + eid);
            }
        }
        redis.delete(INST_PREFIX + instanceId + EXEC_SET_SUFFIX);
        redis.delete(INST_PREFIX + instanceId);
    }

    private void cacheExecution(Execution exec) {
        redis.opsForValue().set(EXEC_PREFIX + exec.getId(), gson.toJson(exec), CACHE_TTL);
        redis.opsForSet().add(INST_PREFIX + exec.getInstanceId() + EXEC_SET_SUFFIX, exec.getId());
        redis.expire(INST_PREFIX + exec.getInstanceId() + EXEC_SET_SUFFIX, CACHE_TTL);
    }

    /** Remove a completed execution from the cache (it remains queryable in DB). */
    private void evictExecution(Execution exec) {
        redis.delete(EXEC_PREFIX + exec.getId());
        redis.opsForSet().remove(INST_PREFIX + exec.getInstanceId() + EXEC_SET_SUFFIX, exec.getId());
    }

    // ── ProcessInstance ──

    @Override
    public void save(ProcessInstance instance) {
        cacheInstance(instance);
        writeToDb(instance);
    }

    @Override
    public ProcessInstance findById(String id) {
        String json = redis.opsForValue().get(INST_PREFIX + id);
        if (json != null) return gson.fromJson(json, ProcessInstance.class);
        // DB fallback for non-RUNNING
        List<ProcessInstance> list = jdbc.query(
            "SELECT * FROM process_instance WHERE id = ?",
            (rs, rowNum) -> mapInstance(rs), id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void update(ProcessInstance instance) {
        if (instance.isRunning()) {
            cacheInstance(instance);
        } else {
            evictInstance(instance.getId());
        }
        writeToDb(instance);
    }

    @Override
    public List<ProcessInstance> findByDefinitionId(String definitionId) {
        return jdbc.query(
            "SELECT * FROM process_instance WHERE definition_id = ? ORDER BY created_at DESC",
            (rs, rowNum) -> mapInstance(rs), definitionId);
    }

    @Override
    public List<ProcessInstance> findAll() {
        Set<String> cachedIds = new HashSet<>();
        List<ProcessInstance> all = new ArrayList<>();
        // Add cached RUNNING instances
        Set<String> instKeys = scanKeys(INST_PREFIX + "*");
        for (String key : instKeys) {
            if (key.endsWith(EXEC_SET_SUFFIX)) continue;
            String json = redis.opsForValue().get(key);
            if (json != null) {
                ProcessInstance inst = gson.fromJson(json, ProcessInstance.class);
                all.add(inst);
                cachedIds.add(inst.getId());
            }
        }
        // Add non-RUNNING from DB
        jdbc.query("SELECT * FROM process_instance WHERE status != 'RUNNING'", (rs) -> {
            ProcessInstance inst = mapInstance(rs);
            if (!cachedIds.contains(inst.getId())) all.add(inst);
        });
        return all;
    }

    @Override
    public List<ProcessInstance> findAllPaginated(int page, int size, String status) {
        boolean hasStatus = status != null && !status.isEmpty();
        String sql = "SELECT * FROM process_instance" +
            (hasStatus ? " WHERE status = ?" : "") + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return hasStatus
            ? jdbc.query(sql, (rs, rowNum) -> mapInstance(rs), status, size, (page - 1) * size)
            : jdbc.query(sql, (rs, rowNum) -> mapInstance(rs), size, (page - 1) * size);
    }

    @Override
    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM process_instance", Long.class);
        return c != null ? c : 0;
    }

    @Override
    public long count(String status) {
        if (status == null || status.isEmpty()) return count();
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM process_instance WHERE status = ?", Long.class, status);
        return c != null ? c : 0;
    }

    @Override
    public List<ProcessInstance> findByDefinitionIdPaginated(String definitionId, int page, int size, String status) {
        boolean hasStatus = status != null && !status.isEmpty();
        String sql = "SELECT * FROM process_instance WHERE definition_id = ?" +
            (hasStatus ? " AND status = ?" : "") + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        Object[] params = hasStatus
            ? new Object[]{definitionId, status, size, (page - 1) * size}
            : new Object[]{definitionId, size, (page - 1) * size};
        return jdbc.query(sql, (rs, rowNum) -> mapInstance(rs), params);
    }

    @Override
    public long countByDefinitionId(String definitionId) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM process_instance WHERE definition_id = ?", Long.class, definitionId);
        return c != null ? c : 0;
    }

    @Override
    public com.github.wf.model.InstanceStats getStats() {
        var s = new com.github.wf.model.InstanceStats();
        // Status counts
        jdbc.query("SELECT status, COUNT(*) c FROM process_instance GROUP BY status", (rs) -> {
            String st = rs.getString("status"); long c = rs.getLong("c");
            s.setTotal(s.getTotal() + c);
            switch (com.github.wf.model.InstanceStatus.valueOf(st)) {
                case RUNNING -> s.setRunning(c);
                case COMPLETED -> s.setCompleted(c);
                case SUSPENDED -> s.setSuspended(c);
                case TERMINATED -> s.setTerminated(c);
            }
        });
        // Avg duration
        Double avg = jdbc.queryForObject(
            "SELECT AVG(completed_at - created_at) FROM process_instance WHERE completed_at IS NOT NULL", Double.class);
        if (avg != null) s.setAvgDurationMs(avg);
        // By definition
        jdbc.query("SELECT definition_id, status, COUNT(*) c FROM process_instance GROUP BY definition_id, status",
            (rs) -> {
                s.getByDefinition()
                    .computeIfAbsent(rs.getString("definition_id"), k -> new java.util.LinkedHashMap<>())
                    .put(rs.getString("status"), rs.getLong("c"));
            });
        return s;
    }

    @Override
    public com.github.wf.model.InstanceStats getStatsByDefinition(String definitionId) {
        var s = new com.github.wf.model.InstanceStats();
        jdbc.query("SELECT status, COUNT(*) c FROM process_instance WHERE definition_id = ? GROUP BY status", (rs) -> {
            String st = rs.getString("status"); long c = rs.getLong("c");
            s.setTotal(s.getTotal() + c);
            switch (com.github.wf.model.InstanceStatus.valueOf(st)) {
                case RUNNING -> s.setRunning(c);
                case COMPLETED -> s.setCompleted(c);
                case SUSPENDED -> s.setSuspended(c);
                case TERMINATED -> s.setTerminated(c);
            }
        }, definitionId);
        Double avg = jdbc.queryForObject(
            "SELECT AVG(completed_at - created_at) FROM process_instance WHERE completed_at IS NOT NULL AND definition_id = ?",
            Double.class, definitionId);
        if (avg != null) s.setAvgDurationMs(avg);
        return s;
    }

    @Override
    public Map<String, Map<String, Long>> getSummary() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        jdbc.query("SELECT definition_id, status, COUNT(*) c FROM process_instance GROUP BY definition_id, status",
            (rs) -> {
                result.computeIfAbsent(rs.getString("definition_id"), k -> {
                    var m = new LinkedHashMap<String, Long>();
                    m.put("running", 0L); m.put("total", 0L);
                    return m;
                });
                var m = result.get(rs.getString("definition_id"));
                long c = rs.getLong("c");
                m.put("total", m.get("total") + c);
                if ("RUNNING".equals(rs.getString("status"))) m.put("running", m.get("running") + c);
            });
        return result;
    }

    @Override
    public void deleteById(String id) {
        evictInstance(id);
        jdbc.update("DELETE FROM execution WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM task WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM historic_activity WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM process_instance WHERE id = ?", id);
    }

    // ── DB write ──

    private void writeToDb(ProcessInstance instance) {
        int updated = jdbc.update(
            "UPDATE process_instance SET status=?, variables_json=?, active_node_ids_json=?, " +
            "parent_instance_id=?, parent_execution_id=?, completed_at=? WHERE id=?",
            instance.getStatus().name(), gson.toJson(instance.getVariables()),
            gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
            instance.getParentInstanceId(),
            instance.getParentExecutionId(),
            instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null,
            instance.getId());
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO process_instance (id, definition_id, definition_version, status, " +
                "variables_json, active_node_ids_json, parent_instance_id, parent_execution_id, " +
                "created_at, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                instance.getId(), instance.getDefinitionId(), instance.getDefinitionVersion(),
                instance.getStatus().name(), gson.toJson(instance.getVariables()),
                gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
                instance.getParentInstanceId(),
                instance.getParentExecutionId(),
                instance.getCreatedAt().toEpochMilli(),
                instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null);
        }
    }

    // ── Execution ──

    @Override
    public void saveExecution(Execution exec) {
        cacheExecution(exec);
        writeExecToDb(exec);
    }

    @Override
    public Execution findExecutionById(String id) {
        String json = redis.opsForValue().get(EXEC_PREFIX + id);
        if (json != null) return gson.fromJson(json, Execution.class);
        // DB fallback
        List<Execution> list = jdbc.query("SELECT * FROM execution WHERE id = ?",
            (rs, rowNum) -> mapExecution(rs), id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Execution> findActiveExecutions(String instanceId) {
        Set<String> execIds = redis.opsForSet().members(INST_PREFIX + instanceId + EXEC_SET_SUFFIX);
        if (execIds == null || execIds.isEmpty()) return List.of();
        // One MGET instead of one GET per execution
        List<String> keys = new ArrayList<>(execIds.size());
        for (String eid : execIds) keys.add(EXEC_PREFIX + eid);
        List<String> jsons = redis.opsForValue().multiGet(keys);
        List<Execution> result = new ArrayList<>();
        if (jsons == null) return result;
        for (int i = 0; i < jsons.size(); i++) {
            String json = jsons.get(i);
            if (json == null) continue;
            Execution exec = gson.fromJson(json, Execution.class);
            if (exec != null && !exec.isCompleted()) result.add(exec);
        }
        return result;
    }

    @Override
    public List<Execution> findExecutionsByParentId(String parentExecutionId) {
        // DB query — join logic must see completed siblings too, and completed
        // executions are evicted from Redis. Uses idx_exec_parent.
        return jdbc.query("SELECT * FROM execution WHERE parent_execution_id = ?",
            (rs, rowNum) -> mapExecution(rs), parentExecutionId);
    }

    @Override
    public void updateExecution(Execution exec) {
        if (exec.isCompleted()) {
            // DB first (source of truth), then evict from cache — the exec set
            // only ever contains live executions, so it can't grow unbounded.
            writeExecToDb(exec);
            evictExecution(exec);
        } else {
            cacheExecution(exec);
            writeExecToDb(exec);
        }
    }

    @Override
    public List<Execution> findPendingTimerRetry() {
        return jdbc.query(
            "SELECT e.* FROM execution e INNER JOIN process_instance i ON e.instance_id = i.id " +
            "WHERE e.status = 'WAITING' AND i.status = 'RUNNING' " +
            "AND e.retry_state IN ('TIMER_PENDING', 'RETRY_PENDING')",
            (rs, rowNum) -> mapExecution(rs));
    }

    private void writeExecToDb(Execution exec) {
        int updated = jdbc.update(
            "UPDATE execution SET current_node_id=?, status=?, retry_attempt=?, next_retry_at=?, retry_state=? WHERE id=?",
            exec.getCurrentNodeId(), exec.getStatus().name(), exec.getRetryAttempt(),
            exec.getNextRetryAt(), exec.getRetryState(), exec.getId());
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO execution (id, instance_id, current_node_id, parent_execution_id, status, retry_attempt, next_retry_at, retry_state) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                exec.getId(), exec.getInstanceId(), exec.getCurrentNodeId(), exec.getParentExecutionId(),
                exec.getStatus().name(), exec.getRetryAttempt(), exec.getNextRetryAt(), exec.getRetryState());
        }
    }

    // ── HistoricActivity ──

    @Override
    public void saveHistoricActivity(HistoricActivity activity) {
        jdbc.update(
            "INSERT INTO historic_activity (id, instance_id, node_id, node_name, node_type, executor, action, timestamp, comment) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            activity.getId(), activity.getInstanceId(), activity.getNodeId(), activity.getNodeName(),
            activity.getNodeType() != null ? activity.getNodeType().name() : null,
            activity.getExecutor(), activity.getAction(),
            activity.getTimestamp().toEpochMilli(), activity.getComment());
    }

    @Override
    public List<HistoricActivity> findHistory(String instanceId) {
        return jdbc.query(
            "SELECT * FROM historic_activity WHERE instance_id = ? ORDER BY timestamp ASC",
            (rs, rowNum) -> mapHistory(rs), instanceId);
    }

    // ── Mappers ──

    private ProcessInstance mapInstance(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        String defId = rs.getString("definition_id");
        int defVer = rs.getInt("definition_version");
        String varsJson = rs.getString("variables_json");
        Map<String, Object> vars = new HashMap<>();
        if (varsJson != null && !varsJson.isEmpty()) {
            Map<String, Object> parsed = gson.fromJson(varsJson, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (parsed != null) vars = parsed;
        }
        String parentInstId = rs.getString("parent_instance_id");
        String parentExecId = rs.getString("parent_execution_id");
        long created = rs.getLong("created_at");
        long completed = rs.getLong("completed_at");
        boolean completedNull = rs.wasNull();
        ProcessInstance inst = new ProcessInstance(id, defId, defVer, vars,
            parentInstId, parentExecId,
            Instant.ofEpochMilli(created),
            completedNull ? null : Instant.ofEpochMilli(completed));
        inst.setStatus(InstanceStatus.valueOf(rs.getString("status")));
        String activeJson = rs.getString("active_node_ids_json");
        if (activeJson != null && !activeJson.isEmpty()) {
            List<String> ids = gson.fromJson(activeJson, new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
            if (ids != null) inst.setActiveNodeIds(new HashSet<>(ids));
        }
        return inst;
    }

    private Execution mapExecution(java.sql.ResultSet rs) throws java.sql.SQLException {
        String parentId = rs.getString("parent_execution_id");
        boolean parentNull = rs.wasNull();
        Execution exec = new Execution(rs.getString("id"), rs.getString("instance_id"),
            rs.getString("current_node_id"), parentNull ? null : parentId);
        exec.setStatus(ExecutionStatus.valueOf(rs.getString("status")));
        exec.setRetryAttempt(rs.getInt("retry_attempt"));
        exec.setNextRetryAt(rs.getLong("next_retry_at"));
        exec.setRetryState(rs.getString("retry_state"));
        return exec;
    }

    private HistoricActivity mapHistory(java.sql.ResultSet rs) throws java.sql.SQLException {
        NodeType nodeType = null;
        String nt = rs.getString("node_type");
        if (nt != null) nodeType = NodeType.valueOf(nt);
        return new HistoricActivity(
            rs.getString("id"), rs.getString("instance_id"),
            rs.getString("node_id"), rs.getString("node_name"), nodeType,
            rs.getString("executor"), rs.getString("action"),
            Instant.ofEpochMilli(rs.getLong("timestamp")), rs.getString("comment"));
    }
}

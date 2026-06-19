package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed InstanceRepository with write-through cache.
 * Cache holds the SAME object references (like InMemory mode),
 * so all mutations are instantly visible everywhere.
 * DB writes happen synchronously for persistence.
 */
public class JdbcInstanceRepository implements InstanceRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    // Write-through caches — same references as InMemory mode
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private final List<HistoricActivity> history = Collections.synchronizedList(new ArrayList<>());

    public JdbcInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Load existing data from DB into cache on startup
        loadAll();
    }

    private void loadAll() {
        jdbc.query("SELECT * FROM process_instance", (rs) -> {
            ProcessInstance inst = mapInstance(rs);
            instances.put(inst.getId(), inst);
        });
        jdbc.query("SELECT * FROM execution", (rs) -> {
            Execution exec = mapExecution(rs);
            executions.put(exec.getId(), exec);
        });
        jdbc.query("SELECT * FROM historic_activity ORDER BY timestamp ASC", (rs) -> {
            history.add(mapHistory(rs));
        });
    }

    // ── ProcessInstance ─────────────────

    @Override
    public void save(ProcessInstance instance) {
        instances.put(instance.getId(), instance);
        writeToDb(instance);
    }

    @Override
    public ProcessInstance findById(String id) {
        return instances.get(id);
    }

    @Override
    public void update(ProcessInstance instance) {
        instances.put(instance.getId(), instance); // same ref
        writeToDb(instance);
    }

    @Override
    public List<ProcessInstance> findByDefinitionId(String definitionId) {
        return instances.values().stream()
                .filter(i -> definitionId.equals(i.getDefinitionId()))
                .toList();
    }

    @Override
    public List<ProcessInstance> findAll() {
        return new ArrayList<>(instances.values());
    }

    @Override
    public void deleteById(String id) {
        instances.remove(id);
        jdbc.update("DELETE FROM execution WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM task WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM historic_activity WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM process_instance WHERE id = ?", id);
    }

    private void writeToDb(ProcessInstance instance) {
        jdbc.update(
            "INSERT INTO process_instance (id, definition_id, definition_version, status, variables_json, active_node_ids_json, created_at, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE status=VALUES(status), variables_json=VALUES(variables_json), " +
            "active_node_ids_json=VALUES(active_node_ids_json), completed_at=VALUES(completed_at)",
            instance.getId(), instance.getDefinitionId(), instance.getDefinitionVersion(),
            instance.getStatus().name(), gson.toJson(instance.getVariables()),
            gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
            instance.getCreatedAt().toEpochMilli(),
            instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null);
    }

    // ── Execution ────────────────────────

    @Override
    public void saveExecution(Execution exec) {
        executions.put(exec.getId(), exec);
        writeExecToDb(exec);
    }

    @Override
    public Execution findExecutionById(String id) {
        return executions.get(id);
    }

    @Override
    public List<Execution> findActiveExecutions(String instanceId) {
        return executions.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId) && !e.isCompleted())
                .toList();
    }

    @Override
    public List<Execution> findExecutionsByParentId(String parentExecutionId) {
        return executions.values().stream()
                .filter(e -> parentExecutionId.equals(e.getParentExecutionId()))
                .toList();
    }

    @Override
    public void updateExecution(Execution exec) {
        executions.put(exec.getId(), exec);
        writeExecToDb(exec);
    }

    private void writeExecToDb(Execution exec) {
        jdbc.update(
            "INSERT INTO execution (id, instance_id, current_node_id, parent_execution_id, status, retry_attempt, next_retry_at, retry_state) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE current_node_id=VALUES(current_node_id), " +
            "status=VALUES(status), retry_attempt=VALUES(retry_attempt), next_retry_at=VALUES(next_retry_at), retry_state=VALUES(retry_state)",
            exec.getId(), exec.getInstanceId(), exec.getCurrentNodeId(), exec.getParentExecutionId(),
            exec.getStatus().name(), exec.getRetryAttempt(), exec.getNextRetryAt(), exec.getRetryState());
    }

    // ── HistoricActivity ─────────────────

    @Override
    public void saveHistoricActivity(HistoricActivity activity) {
        history.add(activity);
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
        return history.stream()
                .filter(h -> h.getInstanceId().equals(instanceId))
                .toList();
    }

    // ── Mapping helpers ──────────────────

    private ProcessInstance mapInstance(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        String defId = rs.getString("definition_id");
        int defVer = rs.getInt("definition_version");
        String varsJson = rs.getString("variables_json");
        Map<String, Object> vars = new HashMap<>();
        if (varsJson != null && !varsJson.isEmpty()) {
            Map<String, Object> parsed = gson.fromJson(varsJson, new TypeToken<Map<String, Object>>() {}.getType());
            if (parsed != null) vars = parsed;
        }
        // Preserve original timestamps
        long created = rs.getLong("created_at");
        long completed = rs.getLong("completed_at");
        ProcessInstance inst = new ProcessInstance(id, defId, defVer, vars);
        inst.setStatus(InstanceStatus.valueOf(rs.getString("status")));
        String activeJson = rs.getString("active_node_ids_json");
        if (activeJson != null && !activeJson.isEmpty()) {
            List<String> ids = gson.fromJson(activeJson, new TypeToken<List<String>>() {}.getType());
            if (ids != null) inst.setActiveNodeIds(new HashSet<>(ids));
        }
        return inst;
    }

    private Execution mapExecution(java.sql.ResultSet rs) throws java.sql.SQLException {
        String parentId = rs.getString("parent_execution_id");
        Execution exec = new Execution(rs.getString("id"), rs.getString("instance_id"),
            rs.getString("current_node_id"), rs.wasNull() ? null : parentId);
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

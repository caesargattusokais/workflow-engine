package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

public class JdbcInstanceRepository implements InstanceRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public JdbcInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── ProcessInstance ─────────────────

    @Override
    public void save(ProcessInstance instance) {
        jdbc.update(
            "INSERT INTO process_instance (id, definition_id, definition_version, status, variables_json, active_node_ids_json, created_at, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            instance.getId(), instance.getDefinitionId(), instance.getDefinitionVersion(),
            instance.getStatus().name(), gson.toJson(instance.getVariables()),
            gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
            instance.getCreatedAt().toEpochMilli(),
            instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null);
    }

    @Override
    public ProcessInstance findById(String id) {
        List<ProcessInstance> list = jdbc.query(
            "SELECT * FROM process_instance WHERE id = ?",
            (rs, rowNum) -> mapInstance(rs), id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void update(ProcessInstance instance) {
        jdbc.update(
            "UPDATE process_instance SET status=?, variables_json=?, active_node_ids_json=?, completed_at=? WHERE id=?",
            instance.getStatus().name(), gson.toJson(instance.getVariables()),
            gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
            instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null,
            instance.getId());
    }

    @Override
    public List<ProcessInstance> findByDefinitionId(String definitionId) {
        return jdbc.query(
            "SELECT * FROM process_instance WHERE definition_id = ?",
            (rs, rowNum) -> mapInstance(rs), definitionId);
    }

    @Override
    public List<ProcessInstance> findAll() {
        return jdbc.query("SELECT * FROM process_instance", (rs, rowNum) -> mapInstance(rs));
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM execution WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM task WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM historic_activity WHERE instance_id = ?", id);
        jdbc.update("DELETE FROM process_instance WHERE id = ?", id);
    }

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
        ProcessInstance inst = new ProcessInstance(id, defId, defVer, vars);
        inst.setStatus(InstanceStatus.valueOf(rs.getString("status")));
        // Override auto-set completedAt for loaded instances
        long completed = rs.getLong("completed_at");
        if (!rs.wasNull() && completed > 0) {
            // completedAt is already set by setStatus for terminal states, fine
        }
        String activeJson = rs.getString("active_node_ids_json");
        if (activeJson != null && !activeJson.isEmpty()) {
            List<String> ids = gson.fromJson(activeJson, new TypeToken<List<String>>() {}.getType());
            if (ids != null) inst.setActiveNodeIds(new HashSet<>(ids));
        }
        return inst;
    }

    // ── Execution ────────────────────────

    @Override
    public void saveExecution(Execution exec) {
        jdbc.update(
            "INSERT INTO execution (id, instance_id, current_node_id, parent_execution_id, status, retry_attempt, next_retry_at, retry_state) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            exec.getId(), exec.getInstanceId(), exec.getCurrentNodeId(), exec.getParentExecutionId(),
            exec.getStatus().name(), exec.getRetryAttempt(), exec.getNextRetryAt(), exec.getRetryState());
    }

    @Override
    public Execution findExecutionById(String id) {
        List<Execution> list = jdbc.query(
            "SELECT * FROM execution WHERE id = ?",
            (rs, rowNum) -> mapExecution(rs), id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Execution> findActiveExecutions(String instanceId) {
        return jdbc.query(
            "SELECT * FROM execution WHERE instance_id = ? AND status != 'COMPLETED'",
            (rs, rowNum) -> mapExecution(rs), instanceId);
    }

    @Override
    public List<Execution> findExecutionsByParentId(String parentExecutionId) {
        return jdbc.query(
            "SELECT * FROM execution WHERE parent_execution_id = ?",
            (rs, rowNum) -> mapExecution(rs), parentExecutionId);
    }

    @Override
    public void updateExecution(Execution exec) {
        jdbc.update(
            "UPDATE execution SET current_node_id=?, status=?, retry_attempt=?, next_retry_at=?, retry_state=? WHERE id=?",
            exec.getCurrentNodeId(), exec.getStatus().name(), exec.getRetryAttempt(),
            exec.getNextRetryAt(), exec.getRetryState(), exec.getId());
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

    // ── HistoricActivity ─────────────────

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
            (rs, rowNum) -> {
                NodeType nodeType = null;
                String nt = rs.getString("node_type");
                if (nt != null) nodeType = NodeType.valueOf(nt);
                HistoricActivity ha = new HistoricActivity(
                    rs.getString("id"), rs.getString("instance_id"),
                    rs.getString("node_id"), rs.getString("node_name"), nodeType,
                    rs.getString("executor"), rs.getString("action"),
                    Instant.ofEpochMilli(rs.getLong("timestamp")), rs.getString("comment"));
                return ha;
            }, instanceId);
    }
}

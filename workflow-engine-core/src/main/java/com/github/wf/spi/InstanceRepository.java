package com.github.wf.spi;

import com.github.wf.engine.Execution;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.InstanceStats;
import com.github.wf.model.ProcessInstance;
import java.util.List;
import java.util.Map;

public interface InstanceRepository {
    void save(ProcessInstance instance);
    ProcessInstance findById(String id);
    void update(ProcessInstance instance);
    List<ProcessInstance> findByDefinitionId(String definitionId);
    default List<ProcessInstance> findByDefinitionIdPaginated(String definitionId, int page, int size) {
        return findByDefinitionId(definitionId).stream().skip((long)(page-1)*size).limit(size).toList();
    }
    default long countByDefinitionId(String definitionId) {
        return findByDefinitionId(definitionId).size();
    }
    default List<ProcessInstance> findAll() { return List.of(); }
    default List<ProcessInstance> findAllPaginated(int page, int size) { return findAll().stream().skip((long)(page-1)*size).limit(size).toList(); }
    default long count() { return findAll().size(); }
    default InstanceStats getStats() {
        InstanceStats s = new InstanceStats();
        for (ProcessInstance i : findAll()) {
            s.setTotal(s.getTotal() + 1);
            switch (i.getStatus()) {
                case RUNNING: s.setRunning(s.getRunning() + 1); break;
                case COMPLETED: s.setCompleted(s.getCompleted() + 1); break;
                case SUSPENDED: s.setSuspended(s.getSuspended() + 1); break;
                case TERMINATED: s.setTerminated(s.getTerminated() + 1); break;
            }
            s.getByDefinition().computeIfAbsent(i.getDefinitionId(), k -> new java.util.LinkedHashMap<>())
                .merge(i.getStatus().name(), 1L, Long::sum);
            if (i.getCompletedAt() != null) {
                long dur = i.getCompletedAt().toEpochMilli() - i.getCreatedAt().toEpochMilli();
                double old = s.getAvgDurationMs();
                s.setAvgDurationMs(old == 0 ? dur : (old + dur) / 2);
            }
        }
        return s;
    }
    /** Return per-definitionId counts: {defId: {running: N, total: N}} */
    default Map<String, Map<String, Long>> getSummary() {
        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        for (ProcessInstance i : findAll()) {
            result.computeIfAbsent(i.getDefinitionId(), k -> {
                var m = new java.util.LinkedHashMap<String, Long>();
                m.put("running", 0L); m.put("total", 0L);
                return m;
            });
            var m = result.get(i.getDefinitionId());
            m.put("total", m.get("total") + 1);
            if (i.isRunning()) m.put("running", m.get("running") + 1);
        }
        return result;
    }

    default void deleteById(String id) {}

    void saveExecution(Execution execution);
    Execution findExecutionById(String id);
    List<Execution> findActiveExecutions(String instanceId);
    List<Execution> findExecutionsByParentId(String parentExecutionId);
    void updateExecution(Execution execution);

    void saveHistoricActivity(HistoricActivity activity);
    List<HistoricActivity> findHistory(String instanceId);

    /** Find all WAITING executions with pending timer/retry states (for recovery). */
    default List<Execution> findPendingTimerRetry() {
        List<Execution> result = new java.util.ArrayList<>();
        for (ProcessInstance i : findAll()) {
            if (!i.isRunning()) continue;
            for (Execution e : findActiveExecutions(i.getId())) {
                if (e.isWaiting() && ("TIMER_PENDING".equals(e.getRetryState())
                        || "RETRY_PENDING".equals(e.getRetryState()))) {
                    result.add(e);
                }
            }
        }
        return result;
    }
}

package com.github.wf.spi;

import com.github.wf.engine.Execution;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.ProcessInstance;
import java.util.List;

public interface InstanceRepository {
    void save(ProcessInstance instance);
    ProcessInstance findById(String id);
    void update(ProcessInstance instance);
    List<ProcessInstance> findByDefinitionId(String definitionId);

    void saveExecution(Execution execution);
    Execution findExecutionById(String id);
    List<Execution> findActiveExecutions(String instanceId);
    List<Execution> findExecutionsByParentId(String parentExecutionId);
    void updateExecution(Execution execution);

    void saveHistoricActivity(HistoricActivity activity);
    List<HistoricActivity> findHistory(String instanceId);
}

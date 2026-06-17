package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.ProcessInstance;
import com.github.wf.spi.InstanceRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryInstanceRepository implements InstanceRepository {

    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private final List<HistoricActivity> history = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(ProcessInstance instance) { instances.put(instance.getId(), instance); }

    @Override
    public ProcessInstance findById(String id) { return instances.get(id); }

    @Override
    public void update(ProcessInstance instance) { instances.put(instance.getId(), instance); }

    @Override
    public List<ProcessInstance> findByDefinitionId(String definitionId) {
        return instances.values().stream()
                .filter(i -> definitionId.equals(i.getDefinitionId()))
                .collect(Collectors.toList());
    }
    public List<ProcessInstance> findAll() {
        return new ArrayList<>(instances.values());
    }
    public void deleteById(String id) { instances.remove(id); }

    @Override
    public void saveExecution(Execution execution) { executions.put(execution.getId(), execution); }

    @Override
    public Execution findExecutionById(String id) { return executions.get(id); }

    @Override
    public List<Execution> findActiveExecutions(String instanceId) {
        return executions.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId))
                .filter(e -> !e.isCompleted())
                .collect(Collectors.toList());
    }

    @Override
    public List<Execution> findExecutionsByParentId(String parentExecutionId) {
        return executions.values().stream()
                .filter(e -> parentExecutionId.equals(e.getParentExecutionId()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateExecution(Execution execution) { executions.put(execution.getId(), execution); }

    @Override
    public void saveHistoricActivity(HistoricActivity activity) { history.add(activity); }

    @Override
    public List<HistoricActivity> findHistory(String instanceId) {
        return history.stream()
                .filter(h -> h.getInstanceId().equals(instanceId))
                .collect(Collectors.toList());
    }
}

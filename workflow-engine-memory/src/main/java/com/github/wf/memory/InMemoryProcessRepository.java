package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import com.github.wf.spi.ProcessRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProcessRepository implements ProcessRepository {

    private final Map<String, ProcessDefinition> store = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestVersion = new ConcurrentHashMap<>();

    @Override
    public void save(ProcessDefinition definition) {
        String key = definition.getId() + ":" + definition.getVersion();
        store.put(key, definition);
        latestVersion.merge(definition.getId(), definition.getVersion(), Math::max);
    }

    @Override
    public ProcessDefinition findById(String id) {
        return store.get(id);
    }

    @Override
    public ProcessDefinition findLatestById(String id) {
        Integer version = latestVersion.get(id);
        if (version == null) return null;
        return store.get(id + ":" + version);
    }

    @Override
    public List<ProcessDefinition> findAllVersions(String id) {
        List<ProcessDefinition> result = new ArrayList<>();
        for (Map.Entry<String, ProcessDefinition> entry : store.entrySet()) {
            if (entry.getKey().startsWith(id + ":")) result.add(entry.getValue());
        }
        result.sort(Comparator.comparingInt(ProcessDefinition::getVersion));
        return result;
    }
}

package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import java.util.*;

public class InMemoryDefinitionRepository implements DefinitionRepository {
    private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

    @Override
    public void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("def", def); e.put("positions", positions);
        store.put(userId + ":" + def.getId() + ":" + def.getVersion(), e);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProcessDefinition> listLatestByUser(String userId) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        store.forEach((k, v) -> {
            if (!k.startsWith(userId + ":")) return;
            ProcessDefinition def = (ProcessDefinition) ((Map<?, ?>) v).get("def");
            latest.merge(def.getId(), def, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        });
        return new ArrayList<>(latest.values());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProcessDefinition findByUserAndId(String userId, String id) {
        return store.entrySet().stream()
            .filter(e -> e.getKey().startsWith(userId + ":" + id + ":"))
            .reduce((a, b) -> b)
            .map(e -> (ProcessDefinition) ((Map<?, ?>) e.getValue()).get("def"))
            .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Double>> findPositions(String userId, String id, Integer version) {
        if (version != null) {
            var e = store.get(userId + ":" + id + ":" + version);
            return e != null ? (Map<String, Map<String, Double>>) ((Map<?, ?>) e).get("positions") : null;
        }
        return store.entrySet().stream()
            .filter(e -> e.getKey().startsWith(userId + ":" + id + ":"))
            .reduce((a, b) -> b)
            .map(e -> (Map<String, Map<String, Double>>) ((Map<?, ?>) e.getValue()).get("positions"))
            .orElse(null);
    }

    @Override
    public void delete(String userId, String id) {
        store.keySet().removeIf(k -> k.startsWith(userId + ":" + id + ":"));
    }
}

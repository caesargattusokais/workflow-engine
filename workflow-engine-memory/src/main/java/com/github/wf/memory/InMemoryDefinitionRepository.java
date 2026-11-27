package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import java.util.*;

public class InMemoryDefinitionRepository implements DefinitionRepository {
    private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

    /** Same predicate as JdbcDefinitionRepository.visibility(): owner OR any caller group. */
    private static boolean visible(Map<String, Object> entry, String userId, List<String> groupIds) {
        if (userId.equals(entry.get("_userId"))) return true;
        Object groupId = entry.get("_groupId");
        return groupId != null && groupIds != null && groupIds.contains(groupId.toString());
    }

    private static ProcessDefinition defOf(Map<String, Object> entry) {
        return (ProcessDefinition) entry.get("def");
    }

    @Override
    public void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        save(userId, null, def, positions);
    }

    @Override
    public void save(String userId, String groupId, ProcessDefinition def,
                     Map<String, Map<String, Double>> positions) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("_userId", userId); e.put("_groupId", groupId);
        e.put("def", def); e.put("positions", positions);
        store.put(userId + ":" + def.getId() + ":" + def.getVersion(), e);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProcessDefinition> listLatestByUser(String userId) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        store.forEach((k, v) -> {
            if (!k.startsWith(userId + ":")) return;
            ProcessDefinition def = defOf(v);
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
            .map(e -> defOf(e.getValue()))
            .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Double>> findPositions(String userId, String id, Integer version) {
        if (version != null) {
            var e = store.get(userId + ":" + id + ":" + version);
            return e != null ? (Map<String, Map<String, Double>>) e.get("positions") : null;
        }
        return store.entrySet().stream()
            .filter(e -> e.getKey().startsWith(userId + ":" + id + ":"))
            .reduce((a, b) -> b)
            .map(e -> (Map<String, Map<String, Double>>) e.getValue().get("positions"))
            .orElse(null);
    }

    @Override
    public void delete(String userId, String id) {
        store.keySet().removeIf(k -> k.startsWith(userId + ":" + id + ":"));
    }

    // ── Group sharing ────────────────────────────────────────────────────
    @Override
    public List<ProcessDefinition> listLatestVisible(String userId, List<String> groupIds) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        store.values().stream().filter(e -> visible(e, userId, groupIds)).forEach(e -> {
            ProcessDefinition def = defOf(e);
            latest.merge(def.getId(), def, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        });
        return latest.values().stream()
                .sorted(Comparator.comparing(ProcessDefinition::getId))
                .toList();
    }

    @Override
    public ProcessDefinition findVisibleByUserAndId(String userId, List<String> groupIds, String id) {
        return store.values().stream()
                .filter(e -> visible(e, userId, groupIds))
                .map(InMemoryDefinitionRepository::defOf)
                .filter(def -> id.equals(def.getId()))
                .max(Comparator.comparingInt(ProcessDefinition::getVersion))
                .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Double>> findVisiblePositions(String userId, List<String> groupIds,
                                                                  String id, Integer version) {
        return store.values().stream()
                .filter(e -> visible(e, userId, groupIds))
                .filter(e -> id.equals(defOf(e).getId()))
                .filter(e -> version == null || defOf(e).getVersion() == version)
                .max(Comparator.comparingInt((Map<String, Object> e) -> defOf(e).getVersion()))
                .map(e -> (Map<String, Map<String, Double>>) e.get("positions"))
                .orElse(null);
    }
}

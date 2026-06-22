package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import java.util.List;
import java.util.Map;

public interface DefinitionRepository {
    void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions);
    List<ProcessDefinition> listLatestByUser(String userId);
    /** Paginated — returns lightweight defs (no nodes/transitions) */
    default List<ProcessDefinition> listLatestByUserPaginated(String userId, int page, int size) {
        return listLatestByUser(userId).stream().skip((long)(page-1)*size).limit(size).toList();
    }
    default long countByUser(String userId) { return listLatestByUser(userId).size(); }
    ProcessDefinition findByUserAndId(String userId, String id);
    Map<String, Map<String, Double>> findPositions(String userId, String id, Integer version);
    void delete(String userId, String id);
}

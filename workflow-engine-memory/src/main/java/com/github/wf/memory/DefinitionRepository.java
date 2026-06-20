package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import java.util.List;
import java.util.Map;

public interface DefinitionRepository {
    void save(String userId, ProcessDefinition def, Map<String, Map<String, Double>> positions);
    List<ProcessDefinition> listLatestByUser(String userId);
    ProcessDefinition findByUserAndId(String userId, String id);
    Map<String, Map<String, Double>> findPositions(String userId, String id, Integer version);
    void delete(String userId, String id);
}

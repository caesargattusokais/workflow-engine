package com.github.wf.memory;

import java.util.List;
import java.util.Map;

public interface DraftRepository {
    List<Map<String, Object>> listByUser(String userId);
    Map<String, Object> findById(String userId, String id);
    Map<String, Object> create(String userId, String name);
    Map<String, Object> copy(String userId, String originalId, String newName);
    Map<String, Object> importDraft(String userId, String name, List<?> nodes, List<?> edges);
    void updateName(String userId, String id, String name);
    void updateNodes(String userId, String id, Object nodes);
    void updateEdges(String userId, String id, Object edges);
    void updateVersion(String userId, String id, int version);
    void delete(String userId, String id);
    boolean nameExists(String userId, String name, String excludeId);
    default List<Map<String, Object>> listByUserPaginated(String userId, int page, int size) {
        return listByUser(userId).stream().skip((long)(page-1)*size).limit(size).toList();
    }
    default long countByUser(String userId) { return listByUser(userId).size(); }
}

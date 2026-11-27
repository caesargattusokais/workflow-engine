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

    // ── Group sharing ────────────────────────────────────────────────────
    default Map<String, Object> create(String userId, String groupId, String name) { return create(userId, name); }
    default List<Map<String, Object>> listVisible(String userId, List<String> groupIds) { return listByUser(userId); }
    default List<Map<String, Object>> listVisiblePaginated(String userId, List<String> groupIds, int page, int size) {
        return listVisible(userId, groupIds).stream().skip((long)(page-1)*size).limit(size).toList();
    }
    default long countVisible(String userId, List<String> groupIds) { return listVisible(userId, groupIds).size(); }
    default Map<String, Object> findVisibleById(String userId, List<String> groupIds, String id) { return findById(userId, id); }
    default void updateName(String userId, List<String> groupIds, String id, String name) { updateName(userId, id, name); }
    default void deleteVisible(String userId, List<String> groupIds, String id) { delete(userId, id); }
    /** Update by primary key — callers MUST have passed findVisibleById first. */
    default void updateNodesById(String id, Object nodes) { throw new UnsupportedOperationException(); }
    default void updateEdgesById(String id, Object edges) { throw new UnsupportedOperationException(); }
    default void updateVersionById(String id, int version) { throw new UnsupportedOperationException(); }
}

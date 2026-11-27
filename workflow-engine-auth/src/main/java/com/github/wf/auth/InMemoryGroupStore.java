package com.github.wf.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory group metadata store, used by the memory profile and unit tests. */
public class InMemoryGroupStore implements GroupStore {

    private final Map<String, GroupInfo> groups = new LinkedHashMap<>();

    public InMemoryGroupStore(AuthProperties props) {
        if (props == null) return;
        Set<String> ids = new LinkedHashSet<>();
        for (AuthProperties.User u : props.getUsers()) {
            if (u.getGroups() != null) ids.addAll(u.getGroups());
            if (u.getAdminGroups() != null) ids.addAll(u.getAdminGroups());
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            String key = id.trim().toLowerCase(Locale.ROOT);
            groups.putIfAbsent(key, new GroupInfo(key, key, "", "config", System.currentTimeMillis()));
        }
    }

    @Override
    public List<GroupInfo> listAll() {
        return new ArrayList<>(groups.values());
    }

    @Override
    public Optional<GroupInfo> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(groups.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void create(GroupInfo info) {
        String key = info.id().toLowerCase(Locale.ROOT);
        if (groups.containsKey(key)) throw GroupException.conflict("群 ID 已存在: " + info.id());
        groups.put(key, info);
    }

    @Override
    public void delete(String id) {
        groups.remove(id.toLowerCase(Locale.ROOT));
    }
}

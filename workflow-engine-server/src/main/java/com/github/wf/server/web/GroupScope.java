package com.github.wf.server.web;

import com.github.wf.engine.InternalVars;
import com.github.wf.model.ProcessInstance;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Group-based data sharing scope. Identity headers are made authoritative by
 * the auth filter's request wrapper, so these values cannot be spoofed by a
 * logged-in browser (in explicit header-compat mode they are supplied by the
 * trusted service caller).
 *
 * <p>Visibility rule: the instance owner always sees their own data; any
 * member of the instance's group sees it too. Group membership is multi-valued
 * — a user's effective group set is the union of X-Group-Ids and the selected
 * current group X-Group-Id.
 */
public final class GroupScope {
    public static final String CURRENT_HEADER = "X-Group-Id";
    public static final String GROUPS_HEADER = "X-Group-Ids";
    public static final String ADMIN_HEADER = "X-Admin-Group-Ids";

    private GroupScope() {}

    public static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
    }

    /** All groups the caller may use for visibility (current group included). */
    public static List<String> groups(HttpServletRequest request) {
        if (request == null) return List.of();
        Set<String> all = new LinkedHashSet<>(csv(request.getHeader(GROUPS_HEADER)));
        all.addAll(csv(request.getHeader(CURRENT_HEADER)));
        return List.copyOf(all);
    }

    public static List<String> adminGroups(HttpServletRequest request) {
        return request == null ? List.of() : csv(request.getHeader(ADMIN_HEADER));
    }

    /** The group new data will be tagged with, or null for private data. */
    public static String currentGroup(HttpServletRequest request) {
        if (request == null) return null;
        String g = request.getHeader(CURRENT_HEADER);
        return g == null || g.isBlank() ? null : g.trim();
    }

    public static boolean isVisible(ProcessInstance instance, String userId, Collection<String> groups) {
        if (instance == null) return false;
        if (userId != null && userId.equals(instance.getVariable(InternalVars.OWNER_KEY))) return true;
        Object group = instance.getVariable(InternalVars.GROUP_KEY);
        return group != null && groups != null && groups.contains(group.toString());
    }

    public static ProcessInstance requireVisible(ProcessInstance instance, String userId, Collection<String> groups, String id) {
        if (!isVisible(instance, userId, groups)) {
            throw new com.github.wf.server.web.NotFoundException("Instance not found: " + id);
        }
        return instance;
    }

    /** Group admins may terminate/recover data shared with their groups. */
    public static boolean isAdmin(ProcessInstance instance, String userId, Collection<String> adminGroups) {
        if (userId != null && userId.equals(instance.getVariable(InternalVars.OWNER_KEY))) return true;
        Object group = instance.getVariable(InternalVars.GROUP_KEY);
        return group != null && adminGroups != null && adminGroups.contains(group.toString());
    }
}

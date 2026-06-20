package com.github.wf.ext;

import java.util.List;

/**
 * SPI for organization structure — LDAP, AD, database, or custom backends.
 * Used by UserTaskRunner to resolve assignees and candidate group membership.
 */
public interface OrgService {

    /** Look up a user by uid. Returns null if not found. */
    OrgUser getUser(String uid);

    /** Get groups a user belongs to. */
    List<String> getGroups(String uid);

    /** Get members of a group. */
    List<String> getGroupMembers(String group);

    /** Get the user's direct manager uid. Returns null if unknown. */
    String getManager(String uid);

    /** Get the user's N-level-up manager (1=direct, 2=skip-level, etc.) */
    default String getManager(String uid, int levels) {
        String current = uid;
        for (int i = 0; i < levels; i++) {
            String mgr = getManager(current);
            if (mgr == null) return null;
            current = mgr;
        }
        return current;
    }

    /** Get the user's direct reports. */
    default List<String> getReports(String uid) { return List.of(); }

    /**
     * Resolve a dynamic role for a given context.
     * Example: role="approver", context="dept-engineering"
     * Returns the uid of the user who fills that role.
     */
    default String resolveRole(String role, String context) { return null; }

    /**
     * Check if a user is a member of a group (including nested groups).
     */
    default boolean isGroupMember(String uid, String group) {
        return getGroups(uid).contains(group);
    }
}

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
}

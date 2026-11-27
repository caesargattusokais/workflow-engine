package com.github.wf.auth;

/**
 * Optional hook implemented by the host application to prevent deleting a
 * group that still owns instances, definitions or drafts. The auth module
 * cannot depend on the repository layer, so the check is injected from the
 * server module. When absent, only group membership is checked.
 */
@FunctionalInterface
public interface GroupUsageChecker {
    boolean inUse(String groupId);
}

package com.github.wf.auth;

import java.util.List;
import java.util.Optional;

/**
 * User storage abstraction. The auth module ships an in-memory implementation
 * seeded from configuration; embedders can provide a JDBC/LDAP-backed bean and
 * it will be picked up instead (see {@code @ConditionalOnMissingBean}).
 */
public interface UserStore {
    Optional<StoredUser> findByUsername(String username);

    /**
     * Persist a new user. Implementations MUST reject a duplicate username and
     * return false (never overwrite an existing account). The password passed
     * here is the raw value; implementations are responsible for hashing it.
     *
     * @return true if the user was created, false if the username already exists
     */
    default boolean create(AuthUser user, String rawPassword) { return false; }

    /** All registered accounts. Needed for group member listings and empty-group checks. */
    default List<AuthUser> listAll() { return List.of(); }

    /**
     * Replace a user's group membership in place. Implementations must keep
     * the password hash and all other account fields intact.
     *
     * @return true when the user existed and was updated
     */
    default boolean updateMembership(String username, List<String> groups, List<String> adminGroups) {
        return false;
    }

    /** Record combining the public principal with the credential material. */
    record StoredUser(AuthUser user, String passwordHash) {}
}

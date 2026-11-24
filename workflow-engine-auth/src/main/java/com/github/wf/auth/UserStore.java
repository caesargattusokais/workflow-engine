package com.github.wf.auth;

import java.util.Optional;

/**
 * User storage abstraction. The auth module ships an in-memory implementation
 * seeded from configuration; embedders can provide a JDBC/LDAP-backed bean and
 * it will be picked up instead (see {@code @ConditionalOnMissingBean}).
 */
public interface UserStore {
    Optional<StoredUser> findByUsername(String username);

    /** Record combining the public principal with the credential material. */
    record StoredUser(AuthUser user, String passwordHash) {}
}

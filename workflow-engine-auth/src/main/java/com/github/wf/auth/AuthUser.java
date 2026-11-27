package com.github.wf.auth;

import java.util.List;
import java.util.Objects;

/**
 * Authenticated principal. Lightweight, framework-agnostic value object.
 *
 * <p>{@code username} is the login name and the identity used downstream as
 * {@code X-User-Id}, so tenant isolation keeps working unchanged.
 */
public final class AuthUser {
    private final String username;
    private final String displayName;
    private final String email;
    private final List<String> roles;
    /** Data-sharing groups this user belongs to (drives X-Group-Ids). */
    private final List<String> groups;
    /** Groups in which this user may terminate/recover shared instances. */
    private final List<String> adminGroups;
    private final boolean enabled;

    public AuthUser(String username, String displayName, String email, List<String> roles, boolean enabled) {
        this(username, displayName, email, roles, List.of(), List.of(), enabled);
    }

    public AuthUser(String username, String displayName, String email, List<String> roles,
                    List<String> groups, List<String> adminGroups, boolean enabled) {
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = displayName == null ? username : displayName;
        this.email = email;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.adminGroups = adminGroups == null ? List.of() : List.copyOf(adminGroups);
        this.enabled = enabled;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
    public List<String> getGroups() { return groups; }
    public List<String> getAdminGroups() { return adminGroups; }
    public boolean isEnabled() { return enabled; }

    /** Public-facing view — never exposes the password hash. */
    public record View(String username, String displayName, String email, List<String> roles,
                       List<String> groups, List<String> adminGroups) {
        public static View of(AuthUser u) {
            return new View(u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(),
                    u.getGroups(), u.getAdminGroups());
        }
    }
}

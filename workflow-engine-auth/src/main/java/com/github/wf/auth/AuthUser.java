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
    private final boolean enabled;

    public AuthUser(String username, String displayName, String email, List<String> roles, boolean enabled) {
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = displayName == null ? username : displayName;
        this.email = email;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.enabled = enabled;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }

    /** Public-facing view — never exposes the password hash. */
    public record View(String username, String displayName, String email, List<String> roles) {
        public static View of(AuthUser u) {
            return new View(u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles());
        }
    }
}

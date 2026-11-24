package com.github.wf.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the auth module.
 *
 * <p>Bootstrap users are seeded on startup from {@code auth.users[*]}. Passwords
 * may be supplied in plain text (convenient for local dev) — they are BCrypt-hashed
 * in memory on load and the plain value is never retained. For production supply
 * a pre-hashed BCrypt value via {@code passwordHash} instead (see docs/auth.md).
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** HMAC signing secret. MUST be overridden in production. */
    private String secret = "wf-dev-only-secret-change-me-please-32b";

    /** Token validity, e.g. 8h, 30m, 7d. */
    private String tokenTtl = "8h";

    /** Cookie name holding the session token. */
    private String cookieName = "wf_session";

    /** Set the Secure flag on the session cookie (enable behind HTTPS). */
    private boolean cookieSecure = false;

    /**
     * Allow the legacy {@code X-User-Id} header as an authentication mechanism.
     * Keep enabled for MCP / E2E service callers; disable in a browser-only production deployment.
     */
    private boolean headerCompatEnabled = true;

    /** Whether login is required at all. Disable only for fully trusted local dev. */
    private boolean enabled = true;

    private List<User> users = new ArrayList<>();

    public static class User {
        private String username;
        private String password;
        private String passwordHash;
        private String displayName;
        private String email;
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(String tokenTtl) { this.tokenTtl = tokenTtl; }
    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public boolean isHeaderCompatEnabled() { return headerCompatEnabled; }
    public void setHeaderCompatEnabled(boolean headerCompatEnabled) { this.headerCompatEnabled = headerCompatEnabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
}

package com.github.wf.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed user store. Registered accounts are persisted so they survive
 * restarts. Bootstrap users from configuration are seeded on construction.
 *
 * <p>The table is created idempotently in the constructor ({@code CREATE TABLE
 * IF NOT EXISTS}) so the module is self-contained and does not depend on the
 * host application's SQL init ordering.
 */
public class JdbcUserStore implements UserStore {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public JdbcUserStore(JdbcTemplate jdbc, AuthProperties props) {
        this.jdbc = jdbc;
        initSchema();
        seed(props);
    }

    private void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS auth_user (" +
                "username VARCHAR(64) NOT NULL, " +
                "display_name VARCHAR(128), " +
                "email VARCHAR(128), " +
                "roles VARCHAR(512), " +
                "enabled INT NOT NULL DEFAULT 1, " +
                "password_hash VARCHAR(100) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (username))");
    }

    /** Seed configured bootstrap users. Existing rows are left untouched. */
    private void seed(AuthProperties props) {
        if (props == null) return;
        for (AuthProperties.User u : props.getUsers()) {
            if (u.getUsername() == null || u.getUsername().isBlank()) continue;
            String hash = (u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                    ? u.getPasswordHash()
                    : (u.getPassword() != null ? encoder.encode(u.getPassword()) : null);
            if (hash == null) continue;
            AuthUser principal = new AuthUser(
                    u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(), u.isEnabled());
            create(principal, hash, true); // already hashed; duplicates ignored
        }
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        List<StoredUser> rows = jdbc.query(
                "SELECT username, display_name, email, roles, enabled, password_hash " +
                "FROM auth_user WHERE username = ?",
                (rs, n) -> {
                    String rolesCsv = rs.getString("roles");
                    List<String> roles = rolesCsv == null || rolesCsv.isBlank()
                            ? List.of() : List.of(rolesCsv.split(","));
                    AuthUser u = new AuthUser(
                            rs.getString("username"), rs.getString("display_name"),
                            rs.getString("email"), roles, rs.getBoolean("enabled"));
                    return new StoredUser(u, rs.getString("password_hash"));
                }, username.toLowerCase());
        return rows.stream().findFirst();
    }

    @Override
    public boolean create(AuthUser user, String rawPassword) {
        return create(user, rawPassword, false);
    }

    private boolean create(AuthUser user, String password, boolean alreadyHashed) {
        String hash = alreadyHashed ? password : encoder.encode(password);
        String roles = user.getRoles() == null ? "" : String.join(",", user.getRoles());
        try {
            jdbc.update("INSERT INTO auth_user " +
                            "(username, display_name, email, roles, enabled, password_hash, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    user.getUsername().toLowerCase(), user.getDisplayName(), user.getEmail(),
                    roles, user.isEnabled() ? 1 : 0, hash, System.currentTimeMillis());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}

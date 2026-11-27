package com.github.wf.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
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
                "groups VARCHAR(512), " +
                "admin_groups VARCHAR(512), " +
                "enabled INT NOT NULL DEFAULT 1, " +
                "password_hash VARCHAR(100) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (username))");
        // CREATE TABLE IF NOT EXISTS does not add columns to a pre-existing
        // table, so upgrade old installations in place.
        ensureColumn("groups", "VARCHAR(512)");
        ensureColumn("admin_groups", "VARCHAR(512)");
    }

    private void ensureColumn(String column, String ddlType) {
        try {
            try (Connection con = jdbc.getDataSource().getConnection()) {
                DatabaseMetaData meta = con.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, "auth_user", column)) {
                    if (rs.next()) return;
                }
            }
        } catch (Exception ignored) {
            // Metadata lookup can fail on restricted accounts; the ALTER below
            // is idempotent in practice thanks to the catch on duplicate column.
        }
        try {
            jdbc.execute("ALTER TABLE auth_user ADD COLUMN " + column + " " + ddlType);
        } catch (Exception ignored) {
            // Column already present (race or metadata denied).
        }
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
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
                    u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(),
                    u.getGroups(), u.getAdminGroups(), u.isEnabled());
            create(principal, hash, true); // already hashed; duplicates ignored
            // Keep configured group membership in sync for bootstrap users —
            // group membership is config-owned, unlike self-registered accounts.
            jdbc.update("UPDATE auth_user SET groups = ?, admin_groups = ? WHERE username = ?",
                    String.join(",", u.getGroups() == null ? List.of() : u.getGroups()),
                    String.join(",", u.getAdminGroups() == null ? List.of() : u.getAdminGroups()),
                    u.getUsername().toLowerCase());
        }
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        List<StoredUser> rows = jdbc.query(
                "SELECT username, display_name, email, roles, groups, admin_groups, enabled, password_hash " +
                "FROM auth_user WHERE username = ?",
                (rs, n) -> {
                    String rolesCsv = rs.getString("roles");
                    List<String> roles = rolesCsv == null || rolesCsv.isBlank()
                            ? List.of() : List.of(rolesCsv.split(","));
                    AuthUser u = new AuthUser(
                            rs.getString("username"), rs.getString("display_name"),
                            rs.getString("email"), roles,
                            csv(rs.getString("groups")), csv(rs.getString("admin_groups")),
                            rs.getBoolean("enabled"));
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
        String groups = user.getGroups() == null ? "" : String.join(",", user.getGroups());
        String adminGroups = user.getAdminGroups() == null ? "" : String.join(",", user.getAdminGroups());
        try {
            jdbc.update("INSERT INTO auth_user " +
                            "(username, display_name, email, roles, groups, admin_groups, enabled, password_hash, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    user.getUsername().toLowerCase(), user.getDisplayName(), user.getEmail(),
                    roles, groups, adminGroups, user.isEnabled() ? 1 : 0, hash, System.currentTimeMillis());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}

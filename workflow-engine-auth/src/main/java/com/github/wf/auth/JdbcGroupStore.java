package com.github.wf.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC-backed group metadata store. The table is created idempotently, like
 * {@link JdbcUserStore}. Groups declared in {@code auth.users[*].groups} are
 * registered automatically so existing deployments see their group names in
 * the management UI without a migration step.
 */
public class JdbcGroupStore implements GroupStore {

    private final JdbcTemplate jdbc;

    public JdbcGroupStore(JdbcTemplate jdbc, AuthProperties props) {
        this.jdbc = jdbc;
        initSchema();
        registerConfigured(props);
    }

    private void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS auth_group (" +
                "group_id VARCHAR(64) NOT NULL, " +
                "name VARCHAR(128), " +
                "description VARCHAR(512), " +
                "created_by VARCHAR(64), " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (group_id))");
    }

    private void registerConfigured(AuthProperties props) {
        if (props == null) return;
        Set<String> ids = new LinkedHashSet<>();
        for (AuthProperties.User u : props.getUsers()) {
            if (u.getGroups() != null) ids.addAll(lower(u.getGroups()));
            if (u.getAdminGroups() != null) ids.addAll(lower(u.getAdminGroups()));
        }
        for (String id : ids) {
            insert(new GroupInfo(id, id, "", "config", System.currentTimeMillis()), false);
        }
    }

    private static List<String> lower(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    @Override
    public List<GroupInfo> listAll() {
        return jdbc.query("SELECT group_id, name, description, created_by, created_at FROM auth_group " +
                        "ORDER BY created_at, group_id",
                (rs, n) -> new GroupInfo(rs.getString("group_id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("created_by"), rs.getLong("created_at")));
    }

    @Override
    public Optional<GroupInfo> find(String id) {
        if (id == null) return Optional.empty();
        List<GroupInfo> rows = jdbc.query(
                "SELECT group_id, name, description, created_by, created_at FROM auth_group WHERE group_id = ?",
                (rs, n) -> new GroupInfo(rs.getString("group_id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("created_by"), rs.getLong("created_at")),
                id.toLowerCase(Locale.ROOT));
        return rows.stream().findFirst();
    }

    @Override
    public void create(GroupInfo info) {
        insert(info, true);
    }

    private void insert(GroupInfo info, boolean failOnDuplicate) {
        try {
            jdbc.update("INSERT INTO auth_group (group_id, name, description, created_by, created_at) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    info.id().toLowerCase(Locale.ROOT), info.name(), info.description(),
                    info.createdBy(), info.createdAt());
        } catch (DuplicateKeyException e) {
            if (failOnDuplicate) throw GroupException.conflict("群 ID 已存在: " + info.id());
        }
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM auth_group WHERE group_id = ?", id.toLowerCase(Locale.ROOT));
    }
}

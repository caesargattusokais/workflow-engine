package com.github.wf.memory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Creates secondary indexes on the workflow tables at startup.
 *
 * Indexes are NOT declared in schema.sql because schema.sql is executed on every
 * startup (spring.sql.init.mode=always) and MySQL does not support
 * {@code CREATE INDEX IF NOT EXISTS}. This initializer is idempotent instead:
 * each statement is executed independently and "index already exists" errors
 * are silently ignored, so it is safe for both MySQL (production) and H2 (tests).
 */
public final class DbIndexInitializer {

    private static final Log log = LogFactory.getLog(DbIndexInitializer.class);

    private static final String[] INDEX_DDL = {
        // process_instance — list by definition, status filter, dashboard aggregates
        "CREATE INDEX idx_pi_def_status_created ON process_instance (definition_id, status, created_at)",
        "CREATE INDEX idx_pi_status ON process_instance (status)",
        "CREATE INDEX idx_pi_user_status ON process_instance (user_id, status)",
        "CREATE INDEX idx_pi_group_status ON process_instance (group_id, status)",
        "CREATE INDEX idx_draft_group ON draft (group_id, name)",
        "CREATE INDEX idx_def_group ON definition (group_id, id)",
        // execution — instance detail view, recover() pending scan
        "CREATE INDEX idx_exec_instance ON execution (instance_id)",
        "CREATE INDEX idx_exec_status_retry ON execution (status, retry_state)",
        "CREATE INDEX idx_exec_parent ON execution (parent_execution_id)",
        // task — task lists by assignee/status, per-instance task panel
        "CREATE INDEX idx_task_instance ON task (instance_id)",
        "CREATE INDEX idx_task_assignee_status ON task (assignee, status)",
        // historic_activity — history timeline query
        "CREATE INDEX idx_hist_instance_ts ON historic_activity (instance_id, timestamp)",
        // draft — per-user draft list
        "CREATE INDEX idx_draft_user ON draft (user_id, name)"
    };

    private DbIndexInitializer() {}

    /**
     * Column migrations for databases created by older versions of schema.sql.
     * {@code CREATE TABLE IF NOT EXISTS} never alters pre-existing tables, so
     * columns added in later releases (sub-process support, retry state, …)
     * would be missing on an upgraded deployment. Each ALTER is executed
     * independently and "duplicate column" errors are ignored, making this
     * idempotent for both MySQL and H2.
     */
    private static final String[][] COLUMN_DDL = {
        // table, column, definition
        {"process_instance", "user_id",             "VARCHAR(64) NULL"},
        {"process_instance", "group_id",            "VARCHAR(64) NULL"},
        {"draft",             "group_id",            "VARCHAR(64) NULL"},
        {"definition",        "group_id",            "VARCHAR(64) NULL"},
        {"process_instance", "definition_version",  "INT NOT NULL DEFAULT 0"},
        {"process_instance", "active_node_ids_json","TEXT"},
        {"process_instance", "parent_instance_id",  "VARCHAR(64) NULL"},
        {"process_instance", "parent_execution_id", "VARCHAR(64) NULL"},
        {"execution",        "parent_execution_id", "VARCHAR(36) NULL"},
        {"execution",        "retry_attempt",       "INT DEFAULT 0"},
        {"execution",        "next_retry_at",       "BIGINT DEFAULT 0"},
        {"execution",        "retry_state",         "VARCHAR(50) NULL"},
        {"task",             "candidate_groups_json","TEXT"},
        {"task",             "variables_json",      "MEDIUMTEXT"},
        {"task",             "completed_at",        "BIGINT NULL"},
        {"historic_activity","node_name",           "VARCHAR(255) NULL"},
        {"historic_activity","node_type",           "VARCHAR(50) NULL"},
        {"historic_activity","executor",            "VARCHAR(255) DEFAULT 'system'"},
        {"historic_activity","comment",             "TEXT NULL"}
    };

    public static void createIndexes(JdbcTemplate jdbc) {
        migrateColumns(jdbc);
        backfillUserId(jdbc);
        for (String ddl : INDEX_DDL) {
            try {
                jdbc.execute(ddl);
                log.info("Index ensured: " + ddl);
            } catch (DataAccessException e) {
                String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : null;
                if (msg != null && (msg.contains("already exists") || msg.contains("Duplicate key name"))) {
                    log.debug("Index already exists, skipped: " + ddl);
                } else {
                    log.warn("Index creation skipped: " + ddl + " — " + msg);
                }
            }
        }
    }

    private static void migrateColumns(JdbcTemplate jdbc) {
        for (String[] col : COLUMN_DDL) {
            String alter = "ALTER TABLE " + col[0] + " ADD COLUMN " + col[1] + " " + col[2];
            try {
                jdbc.execute(alter);
                log.info("Column migration applied: " + alter);
            } catch (DataAccessException e) {
                String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : null;
                if (msg != null && (msg.contains("Duplicate column") || msg.contains("already exists"))) {
                    log.debug("Column already exists, skipped: " + col[0] + "." + col[1]);
                } else {
                    log.warn("Column migration skipped: " + alter + " — " + msg);
                }
            }
        }
    }

    /**
     * Populate process_instance.user_id from the {@code _userId} entry stored in
     * variables_json (rows written before the column existed). Done row-by-row
     * with JSON parsed in Java so it stays portable across MySQL and H2.
     */
    private static void backfillUserId(JdbcTemplate jdbc) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        int updated = 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, variables_json FROM process_instance WHERE user_id IS NULL");
        for (Map<String, Object> row : rows) {
            try {
                String json = (String) row.get("variables_json");
                if (json == null) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> vars = gson.fromJson(json, Map.class);
                Object uid = vars != null ? vars.get("_userId") : null;
                if (uid == null) continue;
                updated += jdbc.update("UPDATE process_instance SET user_id = ? WHERE id = ?",
                    uid.toString(), row.get("id"));
            } catch (Exception e) {
                log.warn("user_id backfill skipped for " + row.get("id") + " — " + e.getMessage());
            }
        }
        if (updated > 0) {
            log.info("user_id backfilled on " + updated + " rows");
        }
    }
}

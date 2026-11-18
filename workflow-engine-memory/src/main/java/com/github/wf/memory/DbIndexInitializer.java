package com.github.wf.memory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

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
}

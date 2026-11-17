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

    public static void createIndexes(JdbcTemplate jdbc) {
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
}

package com.github.wf.memory;

import com.github.wf.engine.InternalVars;
import com.github.wf.model.InstanceStatus;
import com.github.wf.model.ProcessInstance;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisJdbcInstanceRepositoryGroupVisibilityTest {

    @Test
    void redisRepositoryUsesDbGroupIdForVisibilityAndMapping() {
        String url = "jdbc:h2:mem:redis_group_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        createSchema(jdbc);
        insert(jdbc, "i-shared", "def-shared", "fengye", "group-fengye", InstanceStatus.RUNNING);
        insert(jdbc, "i-private", "def-private", "fengye", null, InstanceStatus.COMPLETED);

        // Visibility queries are pushed to SQL; no Redis access is involved.
        RedisJdbcInstanceRepository repo = new RedisJdbcInstanceRepository(jdbc, null, new Gson());

        assertThat(repo.findVisible("qianmeng", List.of("group-fengye"), null))
                .extracting(ProcessInstance::getId).containsExactly("i-shared");
        assertThat(repo.findVisible("qianmeng", List.of(), null)).isEmpty();
        assertThat(repo.findVisiblePaginated("qianmeng", List.of("group-fengye"), null, 1, 10, null))
                .extracting(ProcessInstance::getId).containsExactly("i-shared");
        assertThat(repo.countVisible("qianmeng", List.of("group-fengye"), null, null)).isEqualTo(1);
        assertThat(repo.getVisibleSummary("qianmeng", List.of("group-fengye")))
                .containsKey("def-shared");
        assertThat(repo.getVisibleStats("qianmeng", List.of("group-fengye"), null).getTotal()).isEqualTo(1);

        ProcessInstance shared = repo.findVisible("qianmeng", List.of("group-fengye"), null).get(0);
        assertThat(shared.getVariable(InternalVars.GROUP_KEY)).isEqualTo("group-fengye");

        // JDBC repository applies the same group-variable backfill when mapping DB rows.
        JdbcInstanceRepository jdbcRepo = new JdbcInstanceRepository(jdbc);
        assertThat(jdbcRepo.findById("i-shared").getVariable(InternalVars.GROUP_KEY))
                .isEqualTo("group-fengye");
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE process_instance ("
                + "id VARCHAR(64) PRIMARY KEY,"
                + "definition_id VARCHAR(64),"
                + "definition_version INT,"
                + "status VARCHAR(32),"
                + "variables_json TEXT,"
                + "active_node_ids_json TEXT,"
                + "created_at BIGINT,"
                + "completed_at BIGINT,"
                + "parent_instance_id VARCHAR(64),"
                + "parent_execution_id VARCHAR(64),"
                + "user_id VARCHAR(64),"
                + "group_id VARCHAR(64))");
    }

    private static void insert(JdbcTemplate jdbc, String id, String definitionId, String userId,
                               String groupId, InstanceStatus status) {
        jdbc.update("INSERT INTO process_instance (id, definition_id, definition_version, status, "
                        + "variables_json, active_node_ids_json, created_at, completed_at, "
                        + "parent_instance_id, parent_execution_id, user_id, group_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, definitionId, 1, status.name(), "{}", "[]",
                System.currentTimeMillis(), null, null, null, userId, groupId);
    }
}

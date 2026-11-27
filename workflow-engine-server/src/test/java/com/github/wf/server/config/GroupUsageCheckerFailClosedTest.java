package com.github.wf.server.config;

import com.github.wf.auth.GroupException;
import com.github.wf.auth.GroupUsageChecker;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupUsageCheckerFailClosedTest {

    @Test
    void queryFailureRejectsGroupDeletion() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));
        GroupUsageChecker checker = new RepositoryConfig().groupUsageChecker(dataSource);

        assertThatThrownBy(() -> checker.inUse("g-used"))
                .isInstanceOf(GroupException.class)
                .extracting("status").isEqualTo(500);
    }

    @Test
    void emptyGroupIsUnusedAndPopulatedGroupIsInUse() {
        String url = "jdbc:h2:mem:group_usage_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE process_instance (group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE definition (group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE draft (group_id VARCHAR(64))");
        GroupUsageChecker checker = new RepositoryConfig().groupUsageChecker(dataSource);

        assertThat(checker.inUse("g-empty")).isFalse();

        jdbc.update("INSERT INTO definition (group_id) VALUES ('g-used')");
        assertThat(checker.inUse("g-used")).isTrue();
    }
}

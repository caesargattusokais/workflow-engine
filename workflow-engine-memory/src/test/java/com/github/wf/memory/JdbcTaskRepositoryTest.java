package com.github.wf.memory;

import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTaskRepositoryTest {

    private EmbeddedDatabase db;
    private JdbcTaskRepository repo;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema-h2.sql")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        repo = new JdbcTaskRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ── Helper ──

    private Task newTask(String id, String instanceId, String nodeId) {
        return new Task(id, instanceId, nodeId);
    }

    // ── save + findById ──

    @Test
    void saveAndFindById() {
        Task task = newTask("t1", "inst-1", "review");
        task.setAssignee("zhangsan");
        task.setCandidateGroups(List.of("manager", "hr"));
        task.setVariables(Map.of("approved", true));
        repo.save(task);

        Task found = repo.findById("t1");
        assertThat(found).isNotNull();
        assertThat(found.getInstanceId()).isEqualTo("inst-1");
        assertThat(found.getNodeId()).isEqualTo("review");
        assertThat(found.getAssignee()).isEqualTo("zhangsan");
        assertThat(found.getCandidateGroups()).containsExactly("manager", "hr");
        assertThat(found.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(found.getVariables()).containsEntry("approved", true);
    }

    @Test
    void findByIdReturnsNullForUnknown() {
        assertThat(repo.findById("nonexistent")).isNull();
    }

    // ── update ──

    @Test
    void updateChangesStatus() {
        Task task = newTask("t2", "inst-1", "approve");
        repo.save(task);

        task.setStatus(TaskStatus.COMPLETED);
        repo.update(task);

        Task found = repo.findById("t2");
        assertThat(found.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(found.getCompletedAt()).isNotNull();
    }

    @Test
    void updateChangesAssignee() {
        Task task = newTask("t3", "inst-1", "review");
        task.setAssignee("zhangsan");
        repo.save(task);

        task.setAssignee("lisi");
        repo.update(task);

        Task found = repo.findById("t3");
        assertThat(found.getAssignee()).isEqualTo("lisi");
    }

    @Test
    void delegatedTaskExcludedFromPendingQuery() {
        Task task = newTask("t-delegate", "inst-1", "review");
        task.setAssignee("zhangsan");
        repo.save(task);

        task.setStatus(TaskStatus.DELEGATED);
        repo.update(task);

        List<Task> pending = repo.query(new TaskQuery().status(TaskStatus.PENDING));
        assertThat(pending.stream().noneMatch(t -> t.getId().equals("t-delegate"))).isTrue();
    }

    // ── query by assignee ──

    @Test
    void queryByAssignee() {
        Task t1 = newTask("t1", "inst-1", "node-1");
        t1.setAssignee("zhangsan");
        Task t2 = newTask("t2", "inst-1", "node-2");
        t2.setAssignee("lisi");

        repo.save(t1);
        repo.save(t2);

        List<Task> result = repo.query(new TaskQuery().assignee("zhangsan"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    // ── query by instanceId ──

    @Test
    void queryByInstanceId() {
        Task t1 = newTask("t1", "inst-1", "node-1");
        Task t2 = newTask("t2", "inst-2", "node-1");

        repo.save(t1);
        repo.save(t2);

        List<Task> result = repo.query(new TaskQuery().instanceId("inst-1"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    // ── query by status ──

    @Test
    void queryByStatus() {
        Task pending = newTask("tp", "inst-1", "node-1");
        Task completed = newTask("tc", "inst-1", "node-2");
        completed.setStatus(TaskStatus.COMPLETED);

        repo.save(pending);
        repo.save(completed);

        List<Task> result = repo.query(new TaskQuery().status(TaskStatus.PENDING));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("tp");
    }

    // ── query by candidateGroups (LIKE pattern) ──

    @Test
    void queryByCandidateGroup() {
        Task t1 = newTask("t1", "inst-1", "node-1");
        t1.setCandidateGroups(List.of("manager", "hr"));
        Task t2 = newTask("t2", "inst-1", "node-2");
        t2.setCandidateGroups(List.of("finance"));

        repo.save(t1);
        repo.save(t2);

        List<Task> result = repo.query(new TaskQuery().candidateGroup("manager"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    @Test
    void queryByMultipleCandidateGroups() {
        Task t1 = newTask("t1", "inst-1", "node-1");
        t1.setCandidateGroups(List.of("manager"));
        Task t2 = newTask("t2", "inst-1", "node-2");
        t2.setCandidateGroups(List.of("hr"));
        Task t3 = newTask("t3", "inst-1", "node-3");
        t3.setCandidateGroups(List.of("finance"));

        repo.save(t1);
        repo.save(t2);
        repo.save(t3);

        List<Task> result = repo.query(new TaskQuery().candidateGroups(List.of("manager", "hr")));
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Task::getId).toList())
                .containsExactlyInAnyOrder("t1", "t2");
    }

    // ── LIKE pattern edge case: substring matching ──

    @Test
    void candidateGroupExactMatch() {
        // JSON_CONTAINS ensures exact element matching — "hr" does NOT match "hr-dept"
        Task t1 = newTask("t1", "inst-1", "node-1");
        t1.setCandidateGroups(List.of("hr-dept"));
        Task t2 = newTask("t2", "inst-1", "node-2");
        t2.setCandidateGroups(List.of("hr"));

        repo.save(t1);
        repo.save(t2);

        // Searching for "hr" should ONLY match "hr", NOT "hr-dept"
        List<Task> result = repo.query(new TaskQuery().candidateGroup("hr"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t2");
    }

    // ── combined query ──

    @Test
    void combinedQueryAssigneeAndStatus() {
        Task t1 = newTask("t1", "inst-1", "node-1");
        t1.setAssignee("zhangsan");
        Task t2 = newTask("t2", "inst-1", "node-2");
        t2.setAssignee("zhangsan");
        t2.setStatus(TaskStatus.COMPLETED);

        repo.save(t1);
        repo.save(t2);

        List<Task> result = repo.query(new TaskQuery().assignee("zhangsan").status(TaskStatus.PENDING));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    // ── empty query returns all ──

    @Test
    void emptyQueryReturnsAllTasks() {
        repo.save(newTask("t1", "inst-1", "node-1"));
        repo.save(newTask("t2", "inst-2", "node-2"));

        List<Task> result = repo.query(new TaskQuery());
        assertThat(result).hasSize(2);
    }

    // ── null candidateGroups ──

    @Test
    void taskWithNullCandidateGroups() {
        Task task = newTask("t-null-cg", "inst-1", "node-1");
        // candidateGroups defaults to empty list in Task constructor
        repo.save(task);

        Task found = repo.findById("t-null-cg");
        assertThat(found.getCandidateGroups()).isNotNull();
        assertThat(found.getCandidateGroups()).isEmpty();
    }

    // ── null assignee ──

    @Test
    void taskWithNullAssignee() {
        Task task = newTask("t-null-a", "inst-1", "node-1");
        // assignee defaults to null
        repo.save(task);

        Task found = repo.findById("t-null-a");
        assertThat(found.getAssignee()).isNull();
    }
}

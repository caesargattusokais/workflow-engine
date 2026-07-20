package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcInstanceRepositoryTest {

    private EmbeddedDatabase db;
    private JdbcInstanceRepository repo;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema-h2.sql")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        repo = new JdbcInstanceRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ── ProcessInstance CRUD ──

    @Test
    void saveAndFindById() {
        ProcessInstance inst = new ProcessInstance("inst-1", "wf-test", 1,
                Map.of("applicant", "zhangsan"));
        inst.setActiveNodeIds(java.util.Set.of("review"));
        repo.save(inst);

        ProcessInstance found = repo.findById("inst-1");
        assertThat(found).isNotNull();
        assertThat(found.getDefinitionId()).isEqualTo("wf-test");
        assertThat(found.getDefinitionVersion()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(found.getVariable("applicant")).isEqualTo("zhangsan");
        assertThat(found.getActiveNodeIds()).containsExactly("review");
    }

    @Test
    void findByIdReturnsNullForUnknown() {
        assertThat(repo.findById("nonexistent")).isNull();
    }

    @Test
    void updateChangesStatus() {
        ProcessInstance inst = new ProcessInstance("inst-2", "wf-test", 1, Map.of());
        repo.save(inst);

        inst.setStatus(InstanceStatus.COMPLETED);
        repo.update(inst);

        // After COMPLETED, instance is evicted from cache — falls through to DB query
        ProcessInstance found = repo.findById("inst-2");
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(found.getCompletedAt()).isNotNull();
    }

    @Test
    void updateSuspendedDoesNotSetCompletedAt() {
        ProcessInstance inst = new ProcessInstance("inst-sus", "wf-test", 1, Map.of());
        repo.save(inst);

        inst.setStatus(InstanceStatus.SUSPENDED);
        repo.update(inst);

        // SUSPENDED instances are evicted from cache (not running)
        ProcessInstance found = repo.findById("inst-sus");
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(InstanceStatus.SUSPENDED);
        assertThat(found.getCompletedAt()).isNull();
    }

    @Test
    void updatePreservesVariables() {
        ProcessInstance inst = new ProcessInstance("inst-vars", "wf-test", 1,
                Map.of("key1", "val1", "count", 42));
        repo.save(inst);

        inst.setVariable("key2", "val2");
        repo.update(inst);

        ProcessInstance found = repo.findById("inst-vars");
        assertThat(found.getVariable("key1")).isEqualTo("val1");
        assertThat(found.getVariable("key2")).isEqualTo("val2");
        assertThat(found.getVariable("count")).isEqualTo(42);
    }

    // ── Parent linkage (CallActivity) ──

    @Test
    void savesAndLoadsParentLinkage() {
        ProcessInstance child = new ProcessInstance("child-1", "child-proc", 1,
                Map.of("user", "zhangsan"), "parent-1", "exec-1");
        repo.save(child);

        ProcessInstance found = repo.findById("child-1");
        assertThat(found.getParentInstanceId()).isEqualTo("parent-1");
        assertThat(found.getParentExecutionId()).isEqualTo("exec-1");
    }

    // ── findByDefinitionId ──

    @Test
    void findByDefinitionId() {
        repo.save(new ProcessInstance("i1", "wf-a", 1, Map.of()));
        repo.save(new ProcessInstance("i2", "wf-a", 1, Map.of()));
        repo.save(new ProcessInstance("i3", "wf-b", 1, Map.of()));

        List<ProcessInstance> result = repo.findByDefinitionId("wf-a");
        assertThat(result).hasSize(2);
    }

    // ── Execution CRUD ──

    @Test
    void saveAndFindExecution() {
        Execution exec = new Execution("exec-1", "inst-1", "start");
        repo.saveExecution(exec);

        Execution found = repo.findExecutionById("exec-1");
        assertThat(found).isNotNull();
        assertThat(found.getInstanceId()).isEqualTo("inst-1");
        assertThat(found.getCurrentNodeId()).isEqualTo("start");
        assertThat(found.getStatus()).isEqualTo(ExecutionStatus.ACTIVE);
    }

    @Test
    void updateExecution() {
        Execution exec = new Execution("exec-2", "inst-1", "start");
        repo.saveExecution(exec);

        exec.setCurrentNodeId("review");
        exec.setStatus(ExecutionStatus.WAITING);
        repo.updateExecution(exec);

        Execution found = repo.findExecutionById("exec-2");
        assertThat(found.getCurrentNodeId()).isEqualTo("review");
        assertThat(found.isWaiting()).isTrue();
    }

    @Test
    void findActiveExecutionsFiltersCompleted() {
        Execution active = new Execution("e1", "inst-1", "node-a");
        Execution waiting = new Execution("e2", "inst-1", "node-b");
        waiting.setStatus(ExecutionStatus.WAITING);
        Execution completed = new Execution("e3", "inst-1", "node-c");
        completed.setStatus(ExecutionStatus.COMPLETED);

        repo.saveExecution(active);
        repo.saveExecution(waiting);
        repo.saveExecution(completed);

        List<Execution> activeList = repo.findActiveExecutions("inst-1");
        assertThat(activeList).hasSize(2);
        assertThat(activeList.stream().map(Execution::getId).toList())
                .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    void findActiveExecutionsFiltersByInstanceId() {
        Execution e1 = new Execution("e1", "inst-1", "node-a");
        Execution e2 = new Execution("e2", "inst-2", "node-b");

        repo.saveExecution(e1);
        repo.saveExecution(e2);

        List<Execution> result = repo.findActiveExecutions("inst-1");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("e1");
    }

    @Test
    void findExecutionsByParentId() {
        Execution parent = new Execution("parent-1", "inst-1", "gw");
        Execution child1 = new Execution("c1", "inst-1", "task-a", "parent-1");
        Execution child2 = new Execution("c2", "inst-1", "task-b", "parent-1");
        Execution unrelated = new Execution("c3", "inst-1", "task-c", "parent-2");

        repo.saveExecution(parent);
        repo.saveExecution(child1);
        repo.saveExecution(child2);
        repo.saveExecution(unrelated);

        List<Execution> children = repo.findExecutionsByParentId("parent-1");
        assertThat(children).hasSize(2);
        assertThat(children.stream().map(Execution::getId).toList())
                .containsExactlyInAnyOrder("c1", "c2");
    }

    @Test
    void executionRetryState() {
        Execution exec = new Execution("exec-r", "inst-1", "call");
        exec.setRetryState("RETRY_PENDING");
        exec.setRetryAttempt(2);
        exec.setNextRetryAt(1000L);
        repo.saveExecution(exec);

        Execution found = repo.findExecutionById("exec-r");
        assertThat(found.getRetryState()).isEqualTo("RETRY_PENDING");
        assertThat(found.getRetryAttempt()).isEqualTo(2);
        assertThat(found.getNextRetryAt()).isEqualTo(1000L);
    }

    // ── HistoricActivity ──

    @Test
    void saveAndFindHistory() {
        HistoricActivity enter = HistoricActivity.nodeEnter("inst-1", "start", "开始", NodeType.START_EVENT);
        HistoricActivity leave = HistoricActivity.nodeLeave("inst-1", "start", "开始", NodeType.START_EVENT);
        HistoricActivity complete = HistoricActivity.taskCompleted("inst-1", "review", "审批",
                NodeType.USER_TASK, "zhangsan", "同意");

        repo.saveHistoricActivity(enter);
        repo.saveHistoricActivity(leave);
        repo.saveHistoricActivity(complete);

        List<HistoricActivity> history = repo.findHistory("inst-1");
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getAction()).isEqualTo("enter");
        assertThat(history.get(1).getAction()).isEqualTo("leave");
        assertThat(history.get(2).getExecutor()).isEqualTo("zhangsan");
    }

    @Test
    void findHistoryReturnsEmptyForUnknown() {
        assertThat(repo.findHistory("nonexistent")).isEmpty();
    }

    // ── Cache eviction on COMPLETED ──

    @Test
    void completedInstanceEvictedFromCache() {
        ProcessInstance inst = new ProcessInstance("inst-evict", "wf-test", 1, Map.of());
        repo.save(inst);

        // While running, it's in cache
        assertThat(repo.findById("inst-evict")).isNotNull();

        inst.setStatus(InstanceStatus.COMPLETED);
        repo.update(inst);

        // After completed, evicted from cache but still in DB
        ProcessInstance found = repo.findById("inst-evict");
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // Executions for completed instances are also evicted from cache
        // when the instance is updated to non-RUNNING status
        Execution exec = new Execution("exec-evict", "inst-evict", "end");
        repo.saveExecution(exec);
        // Now update instance to COMPLETED — this evicts executions from cache
        inst.setStatus(InstanceStatus.COMPLETED);
        repo.update(inst);
        // findExecutionById returns from cache — should be evicted
        assertThat(repo.findExecutionById("exec-evict")).isNull();
    }

    // ── Init (load running from DB) ──

    @Test
    void initLoadsRunningInstancesFromDb() {
        // Save a running instance directly
        ProcessInstance inst = new ProcessInstance("inst-recover", "wf-test", 1, Map.of("key", "val"));
        repo.save(inst);

        // Create a new repo instance to simulate restart
        JdbcInstanceRepository freshRepo = new JdbcInstanceRepository(new JdbcTemplate(db));
        freshRepo.init();

        // Should find the running instance
        ProcessInstance found = freshRepo.findById("inst-recover");
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(found.getVariable("key")).isEqualTo("val");
    }
}

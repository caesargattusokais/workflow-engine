package com.github.wf.engine;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.spi.*;
import com.github.wf.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regressions for the three P1 engine-correctness defects:
 * premature timer wake-ups, parallel-join deadlock after task rejection, and
 * infinite token re-execution when a node has no matching outgoing route.
 */
class EngineDeadlockRegressionTest {

    private final Map<String, ProcessDefinition> processes = new HashMap<>();
    private final Map<String, Integer> latestVersions = new HashMap<>();
    private final Map<String, ProcessInstance> instances = new HashMap<>();
    private final Map<String, Execution> executions = new HashMap<>();
    private final Map<String, Task> tasks = new HashMap<>();
    private final List<Long> scheduledDelays = new ArrayList<>();
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        ProcessRepository processRepo = new ProcessRepository() {
            public void save(ProcessDefinition d) {
                processes.put(d.getId() + ":" + d.getVersion(), d);
                latestVersions.merge(d.getId(), d.getVersion(), Math::max);
            }
            public ProcessDefinition findById(String id) { return processes.get(id); }
            public ProcessDefinition findLatestById(String id) {
                Integer v = latestVersions.get(id);
                return v == null ? null : processes.get(id + ":" + v);
            }
            public ProcessDefinition findByIdAndVersion(String id, int version) {
                return processes.get(id + ":" + version);
            }
            public List<ProcessDefinition> findAllVersions(String id) { return List.of(); }
        };

        InstanceRepository instanceRepo = new InstanceRepository() {
            public void save(ProcessInstance i) { instances.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instances.get(id); }
            public void update(ProcessInstance i) { instances.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) {
                return instances.values().stream().filter(i -> i.getDefinitionId().equals(d)).toList();
            }
            public List<ProcessInstance> findAll() { return new ArrayList<>(instances.values()); }
            public void saveExecution(Execution e) { executions.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executions.get(id); }
            public List<Execution> findActiveExecutions(String i) {
                return executions.values().stream()
                        .filter(e -> e.getInstanceId().equals(i) && !e.isCompleted()).toList();
            }
            public List<Execution> findExecutionsByParentId(String p) {
                return executions.values().stream()
                        .filter(e -> p.equals(e.getParentExecutionId())).toList();
            }
            public void updateExecution(Execution e) { executions.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) {}
            public List<HistoricActivity> findHistory(String i) { return List.of(); }
        };

        TaskRepository taskRepo = new TaskRepository() {
            public void save(Task t) { tasks.put(t.getId(), t); }
            public Task findById(String id) { return tasks.get(id); }
            public void update(Task t) { tasks.put(t.getId(), t); }
            public List<Task> query(com.github.wf.task.TaskQuery q) {
                return tasks.values().stream().filter(q::matches).toList();
            }
        };

        // Recording scheduler: no daemon thread, so tests control exactly when a
        // delayed trigger is considered "fired" by calling engine.trigger().
        DelayScheduler scheduler = new DelayScheduler() {
            public void schedule(String instanceId, long delayMs) { scheduledDelays.add(delayMs); }
        };

        engine = WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .delayScheduler(scheduler)
                .build();
    }

    private void deploy(String id, List<Node> nodes, List<Transition> transitions) {
        processRepo().save(new ProcessDefinition(id, id, 1, nodes, transitions));
    }

    private ProcessRepository processRepo() {
        // Only used through deploy(); the engine holds the same implementation.
        return new ProcessRepository() {
            public void save(ProcessDefinition d) {
                processes.put(d.getId() + ":" + d.getVersion(), d);
                latestVersions.merge(d.getId(), d.getVersion(), Math::max);
            }
            public ProcessDefinition findById(String id) { return processes.get(id); }
            public ProcessDefinition findLatestById(String id) {
                Integer v = latestVersions.get(id);
                return v == null ? null : processes.get(id + ":" + v);
            }
            public ProcessDefinition findByIdAndVersion(String id, int version) {
                return processes.get(id + ":" + version);
            }
            public List<ProcessDefinition> findAllVersions(String id) { return List.of(); }
        };
    }

    private Execution onlyExecution(String instanceId) {
        List<Execution> list = engine.instanceRepository.findActiveExecutions(instanceId);
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private List<Task> pendingTasks(String instanceId, String nodeId) {
        return tasks.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId)
                        && t.getNodeId().equals(nodeId) && t.isPending())
                .toList();
    }

    private ProcessDefinition boundaryTimerDef() {
        List<Node> nodes = List.of(
                new StartEvent("start"),
                new UserTask("review", "审批", "manager", null, null, "PT30M", null),
                new EndEvent("normal-end"),
                new EndEvent("timeout-end"));
        List<Transition> transitions = List.of(
                Transition.direct("start", "review"),
                Transition.direct("review", "normal-end"),
                Transition.timeout("review", "timeout-end"));
        return new ProcessDefinition("timer-def", "timer-def", 1, nodes, transitions);
    }

    @Test
    void unrelatedTriggerDoesNotFireUnexpiredBoundaryTimer() {
        processes.put("timer-def:1", boundaryTimerDef());
        latestVersions.put("timer-def", 1);

        ProcessInstance instance = engine.start("timer-def", Map.of());
        Execution exec = onlyExecution(instance.getId());
        assertThat(exec.isWaiting()).isTrue();
        assertThat(exec.isTimerPending()).isTrue();
        assertThat(exec.getNextRetryAt()).isGreaterThan(System.currentTimeMillis());

        // Another branch/timer/recovery trigger on the same instance must not
        // cancel the task or route to the timeout edge before the due time.
        engine.trigger(instance.getId());
        Execution afterEarlyTrigger = onlyExecution(instance.getId());
        assertThat(afterEarlyTrigger.isWaiting()).isTrue();
        assertThat(afterEarlyTrigger.isTimerPending()).isTrue();
        assertThat(afterEarlyTrigger.getCurrentNodeId()).isEqualTo("review");
        assertThat(pendingTasks(instance.getId(), "review")).hasSize(1);

        // Once the persisted due time has elapsed, the timer fires normally.
        afterEarlyTrigger.setNextRetryAt(System.currentTimeMillis() - 1);
        engine.instanceRepository.updateExecution(afterEarlyTrigger);
        engine.trigger(instance.getId());
        assertThat(pendingTasks(instance.getId(), "review")).isEmpty();
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void recoverDefersUnexpiredTimerAndRejectsAlreadyDueOne() {
        processes.put("timer-def:1", boundaryTimerDef());
        latestVersions.put("timer-def", 1);

        ProcessInstance instance = engine.start("timer-def", Map.of());
        onlyExecution(instance.getId());

        scheduledDelays.clear();
        engine.recover();

        Execution afterRecover = onlyExecution(instance.getId());
        assertThat(afterRecover.isWaiting()).isTrue();
        assertThat(afterRecover.isTimerPending()).isTrue();
        assertThat(pendingTasks(instance.getId(), "review")).hasSize(1);
        assertThat(scheduledDelays).anyMatch(delay -> delay > 0 && delay <= 30L * 60 * 1000);

        // Expired timers are still recovered immediately.
        afterRecover.setNextRetryAt(System.currentTimeMillis() - 1000);
        engine.instanceRepository.updateExecution(afterRecover);
        engine.recover();
        assertThat(pendingTasks(instance.getId(), "review")).isEmpty();
    }

    @Test
    void rejectingOneParallelBranchDoesNotDeadlockTheJoin() {
        deploy("parallel-reject",
                List.of(new StartEvent("start"),
                        new ParallelGateway("fork"),
                        new UserTask("finance", "财务审批", "finance", null, null, null, null),
                        new UserTask("legal", "法务审批", "legal", null, null, null, null),
                        new ParallelGateway("join"),
                        new EndEvent("end")),
                List.of(
                        Transition.direct("start", "fork"),
                        Transition.direct("fork", "finance"),
                        Transition.direct("fork", "legal"),
                        Transition.direct("finance", "join"),
                        Transition.direct("legal", "join"),
                        Transition.direct("join", "end")));

        ProcessInstance instance = engine.start("parallel-reject", Map.of());
        Task financeTask = pendingTasks(instance.getId(), "finance").get(0);
        Task legalTask = pendingTasks(instance.getId(), "legal").get(0);

        engine.rejectTask(financeTask.getId(), "材料不全");
        assertThat(pendingTasks(instance.getId(), "finance")).isEmpty();
        assertThat(pendingTasks(instance.getId(), "legal")).hasSize(1);
        assertThat(engine.instanceRepository.findActiveExecutions(instance.getId()).stream()
                .anyMatch(Execution::isRejected)).isTrue();

        // Without terminal-reject handling the join would wait for finance forever.
        engine.completeTask(legalTask.getId(), Map.of(), "同意");
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(engine.instanceRepository.findActiveExecutions(instance.getId())).isEmpty();
    }

    @Test
    void rejectedTaskIsNotRecreatedOnLaterTrigger() {
        deploy("linear-reject",
                List.of(new StartEvent("start"),
                        new UserTask("review", "审批", "reviewer", null, null, null, null),
                        new EndEvent("end")),
                List.of(Transition.direct("start", "review"), Transition.direct("review", "end")));

        ProcessInstance instance = engine.start("linear-reject", Map.of());
        Task task = pendingTasks(instance.getId(), "review").get(0);
        engine.rejectTask(task.getId(), "驳回");

        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.RUNNING);
        engine.trigger(instance.getId());
        assertThat(pendingTasks(instance.getId(), "review")).isEmpty();
        assertThat(onlyExecution(instance.getId()).isRejected()).isTrue();
    }

    /**
     * Redis/JDBC repositories return deserialized COPIES from their queries, so
     * objects the engine holds are not the same references the repository
     * stores. When the parallel join releases, it completes the sibling COPIES
     * but the trigger loop still holds an ACTIVE exec parked at the join — and
     * the no-progress guard then wrongly suspends it, overwriting the
     * COMPLETED write and killing the instance (reproduced on wf-app; the
     * reference-sharing InMemory repo masked the bug). The join must mark the
     * in-memory exec completed too.
     */
    @Test
    void parallelJoinWithRejectReleasesUnderCopySemanticsRepos() {
        WorkflowEngine copyEngine = engineWithCopySemanticsInstanceRepo();

        List<Node> nodes = List.of(new StartEvent("start"),
                new ParallelGateway("fork"),
                new UserTask("finance", "财务审批", "finance", null, null, null, null),
                new UserTask("legal", "法务审批", "legal", null, null, null, null),
                new UserTask("tech", "技术审批", "tech", null, null, null, null),
                new ParallelGateway("join"),
                new UserTask("director", "总监终批", "director", null, null, null, null),
                new EndEvent("end"));
        processRepo().save(new ProcessDefinition("parallel-review", "parallel-review", 1, nodes,
                List.of(Transition.direct("start", "fork"),
                        Transition.direct("fork", "finance"),
                        Transition.direct("fork", "legal"),
                        Transition.direct("fork", "tech"),
                        Transition.direct("finance", "join"),
                        Transition.direct("legal", "join"),
                        Transition.direct("tech", "join"),
                        Transition.direct("join", "director"),
                        Transition.direct("director", "end"))));

        ProcessInstance instance = copyEngine.start("parallel-review", Map.of());
        for (String node : List.of("finance", "legal")) {
            copyEngine.completeTask(pendingTasks(instance.getId(), node).get(0).getId(), Map.of(), "ok");
        }
        // finance and legal are both parked at the join now; reject the third.
        copyEngine.rejectTask(pendingTasks(instance.getId(), "tech").get(0).getId(), "方案不行");

        // The join must release despite the rejected branch: the director task
        // appears, nothing is suspended, and the instance stays RUNNING.
        assertThat(pendingTasks(instance.getId(), "director")).hasSize(1);
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(copyEngine.instanceRepository.findActiveExecutions(instance.getId()))
                .noneMatch(Execution::isSuspended);

        copyEngine.completeTask(pendingTasks(instance.getId(), "director").get(0).getId(), Map.of(), "终批");
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void inclusiveJoinWithRejectReleasesUnderCopySemanticsRepos() {
        WorkflowEngine copyEngine = engineWithCopySemanticsInstanceRepo();

        List<Node> nodes = List.of(new StartEvent("start"),
                new InclusiveGateway("fork"),
                new UserTask("a", "A", "a", null, null, null, null),
                new UserTask("b", "B", "b", null, null, null, null),
                new UserTask("c", "C", "c", null, null, null, null),
                new InclusiveGateway("join"),
                new EndEvent("end"));
        processRepo().save(new ProcessDefinition("inclusive-review", "inclusive-review", 1, nodes,
                List.of(Transition.direct("start", "fork"),
                        Transition.conditional("fork", Condition.expression("1 == 1")).withTo("a"),
                        Transition.conditional("fork", Condition.expression("1 == 1")).withTo("b"),
                        Transition.conditional("fork", Condition.expression("1 == 1")).withTo("c"),
                        Transition.direct("a", "join"),
                        Transition.direct("b", "join"),
                        Transition.direct("c", "join"),
                        Transition.direct("join", "end"))));

        ProcessInstance instance = copyEngine.start("inclusive-review", Map.of());
        for (String node : List.of("a", "b")) {
            copyEngine.completeTask(pendingTasks(instance.getId(), node).get(0).getId(), Map.of(), "ok");
        }
        copyEngine.rejectTask(pendingTasks(instance.getId(), "c").get(0).getId(), "不行");

        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(copyEngine.instanceRepository.findActiveExecutions(instance.getId()))
                .noneMatch(Execution::isSuspended);
    }

    /** Builds a second engine whose instance repo returns COPIES on every read, like Redis/JDBC do. */
    private WorkflowEngine engineWithCopySemanticsInstanceRepo() {
        InstanceRepository base = engine.instanceRepository;
        InstanceRepository copying = new InstanceRepository() {
            public void save(ProcessInstance i) { base.save(i); }
            public ProcessInstance findById(String id) { return base.findById(id); }
            public void update(ProcessInstance i) { base.update(i); }
            public List<ProcessInstance> findByDefinitionId(String d) { return base.findByDefinitionId(d); }
            public List<ProcessInstance> findAll() { return base.findAll(); }
            public void saveExecution(Execution e) { base.saveExecution(e); }
            public Execution findExecutionById(String id) {
                Execution e = base.findExecutionById(id);
                return e == null ? null : copyOf(e);
            }
            public List<Execution> findActiveExecutions(String i) {
                return base.findActiveExecutions(i).stream().map(this::copyOf).toList();
            }
            public List<Execution> findExecutionsByParentId(String p) {
                return base.findExecutionsByParentId(p).stream().map(this::copyOf).toList();
            }
            public void updateExecution(Execution e) { base.updateExecution(e); }
            public void saveHistoricActivity(HistoricActivity h) { base.saveHistoricActivity(h); }
            public List<HistoricActivity> findHistory(String i) { return base.findHistory(i); }
            private Execution copyOf(Execution e) {
                Execution c = new Execution(e.getId(), e.getInstanceId(), e.getCurrentNodeId(), e.getParentExecutionId());
                c.setStatus(e.getStatus());
                c.setRetryState(e.getRetryState());
                c.setRetryAttempt(e.getRetryAttempt());
                c.setNextRetryAt(e.getNextRetryAt());
                return c;
            }
        };
        return WorkflowEngine.builder()
                .processRepository(processRepo())
                .instanceRepository(copying)
                .taskRepository(new TaskRepository() {
                    public void save(Task t) { tasks.put(t.getId(), t); }
                    public Task findById(String id) { return tasks.get(id); }
                    public void update(Task t) { tasks.put(t.getId(), t); }
                    public List<Task> query(com.github.wf.task.TaskQuery q) {
                        return tasks.values().stream().filter(q::matches).toList();
                    }
                })
                .build();
    }

    @Test
    void serviceTaskWithoutMatchingRouteSuspendsInsteadOfSpinning() {
        deploy("svc-no-route",
                List.of(new StartEvent("start"),
                        new ServiceTask("svc", "无路由", "com.test.NoRouteHandler", null),
                        new EndEvent("end")),
                List.of(Transition.direct("start", "svc"),
                        Transition.conditional("svc", Condition.expression("1 == 0")).withTo("end")));

        AtomicInteger calls = new AtomicInteger();
        engine.registerServiceHandler("com.test.NoRouteHandler", vars -> {
            calls.incrementAndGet();
            return Map.of("ok", true);
        });

        ProcessInstance instance = engine.start("svc-no-route", Map.of());
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.SUSPENDED);
        assertThat(onlyExecution(instance.getId()).isSuspended()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(instances.get(instance.getId()).getVariable("svc_suspendReason"))
                .asString().contains("No outgoing transition");
    }

    @Test
    void aiTaskWithoutMatchingRouteSuspendsInsteadOfSpinning() {
        deploy("ai-no-route",
                List.of(new StartEvent("start"),
                        new AiTask("ai", "AI", "summary", "总结", null, null, null, null),
                        new EndEvent("end")),
                List.of(Transition.direct("start", "ai"),
                        Transition.conditional("ai", Condition.expression("1 == 0")).withTo("end")));

        AtomicInteger calls = new AtomicInteger();
        engine.registerAiHandler((system, user, vars) -> {
            calls.incrementAndGet();
            return Map.of("text", "done");
        });

        ProcessInstance instance = engine.start("ai-no-route", Map.of());
        assertThat(instances.get(instance.getId()).getStatus()).isEqualTo(InstanceStatus.SUSPENDED);
        assertThat(onlyExecution(instance.getId()).isSuspended()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void completingTaskWithoutMatchingRouteThrowsAndKeepsTaskPending() {
        deploy("task-no-route",
                List.of(new StartEvent("start"),
                        new UserTask("review", "审批", "reviewer", null, null, null, null),
                        new EndEvent("end")),
                List.of(Transition.direct("start", "review"),
                        Transition.conditional("review", Condition.expression("1 == 0")).withTo("end")));

        ProcessInstance instance = engine.start("task-no-route", Map.of());
        Task task = pendingTasks(instance.getId(), "review").get(0);

        assertThatThrownBy(() -> engine.completeTask(task.getId(), Map.of(), "同意"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No outgoing transition matched");
        assertThat(pendingTasks(instance.getId(), "review")).hasSize(1);
        assertThat(onlyExecution(instance.getId()).isWaiting()).isTrue();
    }
}

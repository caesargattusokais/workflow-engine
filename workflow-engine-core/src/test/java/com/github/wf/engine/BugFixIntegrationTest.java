package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for bug fixes: version-safe definition lookup, ExclusiveGateway default-last,
 * completeTask conditional transitions, delegateTask DELEGATED status,
 * SUSPENDED not setting completedAt, group: assignee candidateGroups.
 */
class BugFixIntegrationTest {

    private WorkflowEngine engine;
    private Map<String, ProcessDefinition> processStore;
    private Map<String, ProcessInstance> instanceStore;
    private Map<String, Execution> executionStore;
    private Map<String, Task> taskStore;
    private Map<String, Integer> latestVersion;
    private List<HistoricActivity> historyStore;

    @BeforeEach
    void setUp() {
        processStore = new HashMap<>();
        instanceStore = new HashMap<>();
        executionStore = new HashMap<>();
        taskStore = new HashMap<>();
        latestVersion = new HashMap<>();
        historyStore = new ArrayList<>();

        engine = WorkflowEngine.builder()
                .processRepository(createProcessRepo())
                .instanceRepository(createInstanceRepo())
                .taskRepository(createTaskRepo())
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

    // ── Repository stubs ──────────────────────────────────────────

    private com.github.wf.spi.ProcessRepository createProcessRepo() {
        return new com.github.wf.spi.ProcessRepository() {
            public void save(ProcessDefinition d) {
                processStore.put(d.getId() + ":" + d.getVersion(), d);
                latestVersion.merge(d.getId(), d.getVersion(), Math::max);
            }
            public ProcessDefinition findById(String id) { return processStore.get(id); }
            public ProcessDefinition findLatestById(String id) {
                Integer v = latestVersion.get(id);
                return v != null ? processStore.get(id + ":" + v) : null;
            }
            public List<ProcessDefinition> findAllVersions(String id) { return List.of(); }
            public ProcessDefinition findByIdAndVersion(String id, int version) {
                return processStore.get(id + ":" + version);
            }
        };
    }

    private com.github.wf.spi.InstanceRepository createInstanceRepo() {
        return new com.github.wf.spi.InstanceRepository() {
            public void save(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instanceStore.get(id); }
            public void update(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) { return List.of(); }
            public void saveExecution(Execution e) { executionStore.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executionStore.get(id); }
            public List<Execution> findActiveExecutions(String i) {
                return executionStore.values().stream()
                        .filter(e -> e.getInstanceId().equals(i) && !e.isCompleted()).toList();
            }
            public List<Execution> findExecutionsByParentId(String p) {
                return executionStore.values().stream()
                        .filter(e -> p.equals(e.getParentExecutionId())).toList();
            }
            public void updateExecution(Execution e) { executionStore.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) { historyStore.add(h); }
            public List<HistoricActivity> findHistory(String i) {
                return historyStore.stream().filter(h -> h.getInstanceId().equals(i)).toList();
            }
        };
    }

    private com.github.wf.spi.TaskRepository createTaskRepo() {
        return new com.github.wf.spi.TaskRepository() {
            public void save(Task t) { taskStore.put(t.getId(), t); }
            public Task findById(String id) { return taskStore.get(id); }
            public void update(Task t) { taskStore.put(t.getId(), t); }
            public List<Task> query(com.github.wf.task.TaskQuery q) {
                return taskStore.values().stream().filter(q::matches).toList();
            }
        };
    }

    // ══════════════════════════════════════════════════════════════
    // #22: Version-safe definition lookup
    // ══════════════════════════════════════════════════════════════

    @Test
    void versionSafeDefinitionLookup_instanceUsesPinnedVersion() {
        // Deploy v1: review → end
        engine.deploy("""
                id: version-test
                name: 版本测试
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: alice
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: end
                """);

        // Start an instance — it pins to v1
        ProcessInstance instance = engine.start("version-test", Map.of());
        assertThat(instance.getDefinitionVersion()).isEqualTo(1);

        // Deploy v2: review → extra-step → end (different flow)
        engine.deploy("""
                id: version-test
                name: 版本测试v2
                version: 2
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: alice
                  - id: extra-step
                    type: userTask
                    name: 额外步骤
                    assignee: bob
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: extra-step
                  - from: extra-step
                    to: end
                """);

        // Verify v2 is now the latest
        ProcessDefinition latest = engine.processRepository.findLatestById("version-test");
        assertThat(latest.getVersion()).isEqualTo(2);

        // Complete the task on the v1 instance — should go to "end" (v1 flow), NOT "extra-step" (v2)
        Task task = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .findFirst().orElseThrow();
        engine.completeTask(task.getId(), Map.of("approved", true), "同意");

        // Instance should be COMPLETED (v1 flow: review → end)
        ProcessInstance afterComplete = instanceStore.get(instance.getId());
        assertThat(afterComplete.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    // ══════════════════════════════════════════════════════════════
    // #23: ExclusiveGateway default branch evaluated last
    // ══════════════════════════════════════════════════════════════

    @Test
    void exclusiveGateway_defaultBranchIsLastResort() {
        // Gateway with default branch listed FIRST in conditions,
        // but conditional branch should still win when it matches
        engine.deploy("""
                id: gw-default-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: user1
                  - id: gw
                    type: exclusiveGateway
                    conditions:
                      - default: true
                        to: low-path
                      - expr: "amount > 1000"
                        to: high-path
                  - id: low-path
                    type: userTask
                    name: 低额审批
                    assignee: low-manager
                  - id: high-path
                    type: userTask
                    name: 高额审批
                    assignee: high-manager
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: gw
                  - from: low-path
                    to: end
                  - from: high-path
                    to: end
                """);

        // Start with amount=5000 — conditional "amount > 1000" should match,
        // NOT the default branch even though it's listed first
        ProcessInstance instance = engine.start("gw-default-test",
                Map.of("amount", 5000));

        // Complete submit task to trigger gateway
        Task submitTask = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId())
                        && t.getNodeId().equals("submit") && t.isPending())
                .findFirst().orElseThrow();
        engine.completeTask(submitTask.getId(), Map.of("amount", 5000), "提交");

        // Should route to high-path (conditional match), NOT low-path (default)
        List<Task> pendingTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .toList();
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getNodeId()).isEqualTo("high-path");
    }

    @Test
    void exclusiveGateway_defaultBranchTakenWhenNoConditionalMatches() {
        engine.deploy("""
                id: gw-default-fallback
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: user1
                  - id: gw
                    type: exclusiveGateway
                    conditions:
                      - expr: "amount > 1000"
                        to: high-path
                      - default: true
                        to: low-path
                  - id: low-path
                    type: userTask
                    name: 低额审批
                    assignee: low-manager
                  - id: high-path
                    type: userTask
                    name: 高额审批
                    assignee: high-manager
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: gw
                  - from: low-path
                    to: end
                  - from: high-path
                    to: end
                """);

        // Start with amount=100 — no conditional matches, default should be taken
        ProcessInstance instance = engine.start("gw-default-fallback",
                Map.of("amount", 100));

        Task submitTask = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId())
                        && t.getNodeId().equals("submit") && t.isPending())
                .findFirst().orElseThrow();
        engine.completeTask(submitTask.getId(), Map.of("amount", 100), "提交");

        List<Task> pendingTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .toList();
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getNodeId()).isEqualTo("low-path");
    }

    // ══════════════════════════════════════════════════════════════
    // #24: Timeout routing cancels PENDING task
    // ══════════════════════════════════════════════════════════════

    @Test
    void timeoutRouting_cancelsPendingTask() throws Exception {
        engine.deploy("""
                id: timeout-cancel-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: reviewer
                    boundaryTimer: "PT1S"
                  - id: escalated
                    type: userTask
                    name: 升级处理
                    assignee: director
                  - id: end1
                    type: endEvent
                  - id: end2
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: end1
                    type: direct
                  - from: review
                    to: escalated
                    type: timeout
                  - from: escalated
                    to: end2
                """);

        ProcessInstance instance = engine.start("timeout-cancel-test", Map.of());
        String instanceId = instance.getId();

        // Verify PENDING task exists for "review"
        List<Task> reviewTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId)
                        && t.getNodeId().equals("review") && t.isPending())
                .toList();
        assertThat(reviewTasks).hasSize(1);
        Task reviewTask = reviewTasks.get(0);

        // Wait for boundary timer to fire
        Thread.sleep(3000);

        // The original review task should now be REJECTED (cancelled by timeout)
        Task afterTimeout = taskStore.get(reviewTask.getId());
        assertThat(afterTimeout.getStatus()).isEqualTo(TaskStatus.REJECTED);

        // A new task should exist for "escalated"
        List<Task> escalatedTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId)
                        && t.getNodeId().equals("escalated") && t.isPending())
                .toList();
        assertThat(escalatedTasks).hasSize(1);
    }

    // ══════════════════════════════════════════════════════════════
    // #25: completeTask evaluates conditional transitions
    // ══════════════════════════════════════════════════════════════

    @Test
    void completeTask_evaluatesConditionalTransitions() {
        // UserTask with conditional outgoing transitions using type: conditional + expr:
        engine.deploy("""
                id: conditional-complete-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: applicant
                  - id: approved-end
                    type: endEvent
                  - id: rejected-end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: approved-end
                    type: conditional
                    expr: "approved == true"
                  - from: submit
                    to: rejected-end
                    type: conditional
                    expr: "approved == false"
                """);

        ProcessInstance instance = engine.start("conditional-complete-test",
                Map.of("applicant", "张三"));

        // Complete task with approved=false → should route to rejected-end
        Task task = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .findFirst().orElseThrow();
        engine.completeTask(task.getId(), Map.of("approved", false), "拒绝");

        // Should have gone to rejected-end → COMPLETED
        ProcessInstance afterComplete = instanceStore.get(instance.getId());
        assertThat(afterComplete.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void completeTask_conditionalWithDefaultFallback() {
        engine.deploy("""
                id: conditional-default-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: applicant
                  - id: special-path
                    type: endEvent
                  - id: default-path
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: special-path
                    type: conditional
                    expr: "score > 90"
                  - from: submit
                    to: default-path
                    type: default
                """);

        ProcessInstance instance = engine.start("conditional-default-test",
                Map.of("applicant", "李四"));

        // Complete with score=50 — no conditional matches, default should be taken
        Task task = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .findFirst().orElseThrow();
        engine.completeTask(task.getId(), Map.of("score", 50), "提交");

        ProcessInstance afterComplete = instanceStore.get(instance.getId());
        assertThat(afterComplete.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    // ══════════════════════════════════════════════════════════════
    // #26: delegateTask sets old task to DELEGATED
    // ══════════════════════════════════════════════════════════════

    @Test
    void delegateTask_oldTaskBecomesDelegated() {
        engine.deploy("""
                id: delegate-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: manager1
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: end
                """);

        ProcessInstance instance = engine.start("delegate-test", Map.of());

        // Find the original task
        Task originalTask = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId())
                        && t.getNodeId().equals("review") && t.isPending())
                .findFirst().orElseThrow();
        assertThat(originalTask.getAssignee()).isEqualTo("manager1");
        assertThat(originalTask.getStatus()).isEqualTo(TaskStatus.PENDING);

        // Delegate to manager2
        engine.delegateTask(originalTask.getId(), "manager2");

        // Old task should be DELEGATED
        Task oldTask = taskStore.get(originalTask.getId());
        assertThat(oldTask.getStatus()).isEqualTo(TaskStatus.DELEGATED);

        // New task should be PENDING with manager2
        List<Task> newTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId())
                        && t.getNodeId().equals("review")
                        && t.getAssignee().equals("manager2")
                        && t.isPending())
                .toList();
        assertThat(newTasks).hasSize(1);

        // Old DELEGATED task should NOT appear in pending queries
        List<Task> pendingTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .toList();
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getAssignee()).isEqualTo("manager2");
    }

    // ══════════════════════════════════════════════════════════════
    // #27: SUSPENDED does not set completedAt
    // ══════════════════════════════════════════════════════════════

    @Test
    void suspendedInstance_doesNotSetCompletedAt() {
        engine.deploy("""
                id: suspend-completedAt-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    name: 调用
                    handlerClass: "com.test.FailHandler"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                  - from: call
                    to: end
                """);

        engine.registerServiceHandler("com.test.FailHandler", vars -> {
            throw new RuntimeException("intentional failure");
        });

        ProcessInstance instance = engine.start("suspend-completedAt-test", Map.of());

        // Instance should be SUSPENDED
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.SUSPENDED);

        // completedAt should NOT be set for SUSPENDED
        assertThat(instance.getCompletedAt()).isNull();

        // Now fix the handler and resume
        engine.registerServiceHandler("com.test.FailHandler",
                vars -> Map.of("fixed", true));
        engine.resume(instance.getId());

        // After resume, instance should be COMPLETED
        ProcessInstance afterResume = instanceStore.get(instance.getId());
        assertThat(afterResume.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // completedAt should now be set
        assertThat(afterResume.getCompletedAt()).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════
    // #28: group: assignee adds to candidateGroups
    // ══════════════════════════════════════════════════════════════

    @Test
    void groupAssignee_addsToCandidateGroups() {
        engine.deploy("""
                id: group-assignee-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 组审批
                    assignee: "group:managers"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: end
                """);

        ProcessInstance instance = engine.start("group-assignee-test", Map.of());

        // Task should have "managers" in candidateGroups, NOT as assignee
        Task task = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .findFirst().orElseThrow();

        assertThat(task.getAssignee()).isNull();
        assertThat(task.getCandidateGroups()).contains("managers");
    }

    @Test
    void groupAssignee_combinedWithExistingCandidateGroups() {
        engine.deploy("""
                id: group-combined-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 组审批
                    assignee: "group:approvers"
                    candidateGroups: ["reviewers"]
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: end
                """);

        ProcessInstance instance = engine.start("group-combined-test", Map.of());

        Task task = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending())
                .findFirst().orElseThrow();

        // Should have both "reviewers" (from candidateGroups) and "approvers" (from group: assignee)
        assertThat(task.getCandidateGroups()).containsExactlyInAnyOrder("reviewers", "approvers");
        assertThat(task.getAssignee()).isNull();
    }
}

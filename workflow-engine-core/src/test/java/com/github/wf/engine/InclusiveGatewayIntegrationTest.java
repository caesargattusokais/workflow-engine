package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for InclusiveGateway — evaluates conditions and forks matching branches.
 */
class InclusiveGatewayIntegrationTest {

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

    // ── Repository stubs ──

    private com.github.wf.spi.ProcessRepository createProcessRepo() {
        return new com.github.wf.spi.ProcessRepository() {
            public void save(ProcessDefinition d) {
                processStore.put(d.getId() + ":" + d.getVersion(), d);
                latestVersion.merge(d.getId(), d.getVersion(), Math::max);
            }
            public ProcessDefinition findById(String id) { return findLatestById(id); }
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
            public List<Execution> findActiveExecutions(String instId) {
                return executionStore.values().stream()
                        .filter(e -> e.getInstanceId().equals(instId) && !e.isCompleted()).toList();
            }
            public List<Execution> findExecutionsByParentId(String parentId) {
                return executionStore.values().stream()
                        .filter(e -> parentId.equals(e.getParentExecutionId())).toList();
            }
            public void updateExecution(Execution e) { executionStore.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity a) { historyStore.add(a); }
            public List<HistoricActivity> findHistory(String instId) {
                return historyStore.stream().filter(h -> h.getInstanceId().equals(instId)).toList();
            }
        };
    }

    private com.github.wf.spi.TaskRepository createTaskRepo() {
        return new com.github.wf.spi.TaskRepository() {
            public void save(Task t) { taskStore.put(t.getId(), t); }
            public Task findById(String id) { return taskStore.get(id); }
            public void update(Task t) { taskStore.put(t.getId(), t); }
            public List<Task> query(TaskQuery q) {
                return taskStore.values().stream().filter(q::matches).toList();
            }
        };
    }

    // ── Tests ──

    @Test
    void multipleBranchesMatch_forksInParallel() {
        // Inclusive gateway where 2 of 3 conditions match → 2 parallel branches
        engine.deploy("""
                id: inclusive-multi
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: "${applicant}"
                  - id: gw
                    type: inclusiveGateway
                    conditions:
                      - expr: "amount > 1000"
                        to: finance-approve
                      - expr: "days > 3"
                        to: hr-approve
                      - default: true
                        to: end
                  - id: finance-approve
                    type: userTask
                    name: 财务审批
                    assignee: "finance"
                  - id: hr-approve
                    type: userTask
                    name: HR审批
                    assignee: "hr"
                  - id: join
                    type: inclusiveGateway
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: gw
                  - from: finance-approve
                    to: join
                  - from: hr-approve
                    to: join
                  - from: join
                    to: end
                """);

        // Start with amount=5000 and days=5 — both conditions match
        ProcessInstance inst = engine.start("inclusive-multi",
                Map.of("applicant", "zhangsan", "amount", 5000, "days", 5));

        // Complete the submit task
        List<Task> submitTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(submitTasks).hasSize(1);
        engine.completeTask(submitTasks.get(0).getId(), Map.of(), "提交");

        // After submit, inclusive gateway should fork 2 branches (finance + hr)
        List<Task> pendingTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(pendingTasks).hasSize(2);

        // Verify both tasks are for the correct assignees
        List<String> assignees = pendingTasks.stream().map(Task::getAssignee).toList();
        assertThat(assignees).containsExactlyInAnyOrder("finance", "hr");

        // Complete both tasks — instance should complete through join
        for (Task t : pendingTasks) {
            engine.completeTask(t.getId(), Map.of(), "审批通过");
        }

        inst = engine.instanceRepository.findById(inst.getId());
        assertThat(inst.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void singleBranchMatch_takesOneBranch() {
        // Only one condition matches → single branch (no parallel fork needed)
        engine.deploy("""
                id: inclusive-single
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: "${applicant}"
                  - id: gw
                    type: inclusiveGateway
                    conditions:
                      - expr: "amount > 1000"
                        to: finance-approve
                      - expr: "days > 3"
                        to: hr-approve
                      - default: true
                        to: end
                  - id: finance-approve
                    type: userTask
                    name: 财务审批
                    assignee: "finance"
                  - id: hr-approve
                    type: userTask
                    name: HR审批
                    assignee: "hr"
                  - id: join
                    type: inclusiveGateway
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: gw
                  - from: finance-approve
                    to: join
                  - from: hr-approve
                    to: join
                  - from: join
                    to: end
                """);

        // amount=5000, days=1 — only amount condition matches
        ProcessInstance inst = engine.start("inclusive-single",
                Map.of("applicant", "zhangsan", "amount", 5000, "days", 1));

        List<Task> submitTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(submitTasks).hasSize(1);
        engine.completeTask(submitTasks.get(0).getId(), Map.of(), "提交");

        // Only finance task should be created
        List<Task> pendingTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getAssignee()).isEqualTo("finance");

        // Complete finance task
        engine.completeTask(pendingTasks.get(0).getId(), Map.of(), "审批通过");

        inst = engine.instanceRepository.findById(inst.getId());
        assertThat(inst.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void noBranchMatch_takesDefault() {
        // No conditions match → default branch taken
        engine.deploy("""
                id: inclusive-default
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: submit
                    type: userTask
                    name: 提交
                    assignee: "${applicant}"
                  - id: gw
                    type: inclusiveGateway
                    conditions:
                      - expr: "amount > 1000"
                        to: finance-approve
                      - expr: "days > 3"
                        to: hr-approve
                      - default: true
                        to: simple-approve
                  - id: finance-approve
                    type: userTask
                    name: 财务审批
                    assignee: "finance"
                  - id: hr-approve
                    type: userTask
                    name: HR审批
                    assignee: "hr"
                  - id: simple-approve
                    type: userTask
                    name: 简单审批
                    assignee: "manager"
                  - id: join
                    type: inclusiveGateway
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: submit
                  - from: submit
                    to: gw
                  - from: finance-approve
                    to: join
                  - from: hr-approve
                    to: join
                  - from: simple-approve
                    to: end
                  - from: join
                    to: end
                """);

        // amount=100, days=1 — neither condition matches → default
        ProcessInstance inst = engine.start("inclusive-default",
                Map.of("applicant", "zhangsan", "amount", 100, "days", 1));

        List<Task> submitTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(submitTasks).hasSize(1);
        engine.completeTask(submitTasks.get(0).getId(), Map.of(), "提交");

        // Default branch → simple-approve task
        List<Task> pendingTasks = engine.queryTasks(new TaskQuery().instanceId(inst.getId()).status(TaskStatus.PENDING));
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getAssignee()).isEqualTo("manager");

        // Complete simple-approve → instance completes
        engine.completeTask(pendingTasks.get(0).getId(), Map.of(), "审批通过");

        inst = engine.instanceRepository.findById(inst.getId());
        assertThat(inst.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }
}

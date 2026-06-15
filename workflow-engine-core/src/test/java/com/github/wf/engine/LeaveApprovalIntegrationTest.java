package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveApprovalIntegrationTest {

    private WorkflowEngine engine;
    private final Map<String, ProcessDefinition> processStore = new HashMap<>();
    private final Map<String, ProcessInstance> instanceStore = new HashMap<>();
    private final Map<String, Execution> executionStore = new HashMap<>();
    private final Map<String, Task> taskStore = new HashMap<>();
    private final Map<String, Integer> latestVersion = new HashMap<>();
    private final List<HistoricActivity> historyStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = WorkflowEngine.builder()
                .processRepository(createProcessRepo())
                .instanceRepository(createInstanceRepo())
                .taskRepository(createTaskRepo())
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

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

    @Test
    void fullLeaveApprovalFlow() {
        engine.deploy("""
                id: leave-approval
                name: 请假审批
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: apply
                    type: userTask
                    name: 提交请假申请
                    assignee: "${applicant}"
                  - id: gateway
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 3"
                        to: manager-approve
                      - default: true
                        to: department-manager
                  - id: manager-approve
                    type: userTask
                    name: 总经理审批
                    candidateGroups: ["manager"]
                  - id: department-manager
                    type: userTask
                    name: 部门经理审批
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: apply
                  - from: apply
                    to: gateway
                  - from: manager-approve
                    to: end
                  - from: department-manager
                    to: end
                """);

        // Start with 5 days — should go to manager
        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "张三", "days", 5));
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        // Applicant submits
        List<Task> applyTasks = taskStore.values().stream()
                .filter(t -> t.getAssignee().equals("张三") && t.isPending()).toList();
        assertThat(applyTasks).hasSize(1);
        engine.completeTask(applyTasks.get(0).getId(), Map.of("reason", "看病"), "看病请假");

        // Should route to manager (days > 3)
        List<Task> managerTasks = taskStore.values().stream()
                .filter(t -> t.getCandidateGroups().contains("manager") && t.isPending()).toList();
        assertThat(managerTasks).hasSize(1);
        assertThat(managerTasks.get(0).getNodeId()).isEqualTo("manager-approve");

        // Manager approves
        engine.completeTask(managerTasks.get(0).getId(), Map.of("approved", true), "同意");

        // Flow complete
        ProcessInstance completed = instanceStore.get(instance.getId());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // History recorded
        List<HistoricActivity> history = historyStore.stream()
                .filter(h -> h.getInstanceId().equals(instance.getId())).toList();
        assertThat(history).isNotEmpty();
    }

    @Test
    void leaveApprovalShortLeaveGoesToDepartmentManager() {
        engine.deploy("""
                id: leave-approval
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: apply
                    type: userTask
                    name: 提交请假申请
                    assignee: "${applicant}"
                  - id: gateway
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 3"
                        to: manager-approve
                      - default: true
                        to: department-manager
                  - id: manager-approve
                    type: userTask
                    name: 总经理审批
                  - id: department-manager
                    type: userTask
                    name: 部门经理审批
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: apply
                  - from: apply
                    to: gateway
                  - from: manager-approve
                    to: end
                  - from: department-manager
                    to: end
                """);

        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "李四", "days", 1));

        List<Task> applyTasks = taskStore.values().stream()
                .filter(t -> t.getAssignee().equals("李四") && t.isPending()).toList();
        engine.completeTask(applyTasks.get(0).getId(), Map.of(), "请假1天");

        // Should go to department manager (default branch, days <= 3)
        List<Task> pending = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId()) && t.isPending()).toList();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getNodeId()).isEqualTo("department-manager");
    }
}

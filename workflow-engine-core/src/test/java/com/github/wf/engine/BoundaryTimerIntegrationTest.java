package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for UserTask boundary timer:
 * UserTask with boundaryTimer="PT1S" + timeout edge → escalation node.
 * The timer fires after ~1 second and routes to the timeout target.
 */
class BoundaryTimerIntegrationTest {

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
    void boundaryTimerFiresAndRoutesToTimeoutEdge() throws Exception {
        // Deploy a workflow with UserTask that has boundaryTimer + timeout edge
        engine.deploy("""
                id: bt-test
                name: 边界定时器测试
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: manager
                    boundaryTimer: "PT1S"
                  - id: escalated
                    type: userTask
                    name: 升级处理
                    assignee: director
                  - id: normal-end
                    type: endEvent
                  - id: timeout-end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: normal-end
                    type: direct
                  - from: review
                    to: escalated
                    type: timeout
                  - from: escalated
                    to: timeout-end
                """);

        // Start instance
        ProcessInstance instance = engine.start("bt-test", Map.of());
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        String instanceId = instance.getId();

        // Verify a task was created for the UserTask "review"
        List<Task> reviewTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId) && t.isPending() && t.getNodeId().equals("review"))
                .toList();
        assertThat(reviewTasks).hasSize(1);

        // Verify execution is WAITING + TIMER_PENDING
        List<Execution> execs = executionStore.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId) && !e.isCompleted()).toList();
        assertThat(execs).hasSize(1);
        Execution exec = execs.get(0);
        assertThat(exec.isWaiting()).isTrue();
        assertThat(exec.getRetryState()).isEqualTo("TIMER_PENDING");
        assertThat(exec.getCurrentNodeId()).isEqualTo("review");

        // Wait for the boundary timer to fire (PT1S + buffer)
        System.err.println("[BT-TEST] Waiting for boundary timer to fire...");
        Thread.sleep(3000);

        // After timer fires, instance should have advanced
        ProcessInstance afterTimer = instanceStore.get(instanceId);
        System.err.println("[BT-TEST] After timer wait — instance status: " + afterTimer.getStatus()
                + " activeNodes: " + afterTimer.getActiveNodeIds());

        // Get current executions
        List<Execution> currentExecs = executionStore.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId) && !e.isCompleted()).toList();
        System.err.println("[BT-TEST] Active executions after timer: " + currentExecs.size());
        for (Execution e : currentExecs) {
            System.err.println("[BT-TEST]   exec: node=" + e.getCurrentNodeId()
                    + " status=" + e.getStatus() + " retryState=" + e.getRetryState());
        }

        // The execution should have moved to "escalated" (timeout target)
        boolean escalatedReached = currentExecs.stream()
                .anyMatch(e -> "escalated".equals(e.getCurrentNodeId()));
        assertThat(escalatedReached)
                .as("Timeout should have routed execution to 'escalated' node")
                .isTrue();

        // Verify a task was created for "escalated"
        List<Task> escalatedTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId) && t.isPending() && t.getNodeId().equals("escalated"))
                .toList();
        assertThat(escalatedTasks).hasSize(1);
    }

    @Test
    void manualCompleteRoutesToDirectEdgeNotTimeout() {
        // Same workflow as above
        engine.deploy("""
                id: bt-manual
                name: 手动完成测试
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: review
                    type: userTask
                    name: 审批
                    assignee: manager
                    boundaryTimer: "PT30M"
                  - id: normal-end
                    type: endEvent
                  - id: escalated
                    type: userTask
                    name: 升级处理
                    assignee: director
                  - id: timeout-end
                    type: endEvent
                transitions:
                  - from: start
                    to: review
                  - from: review
                    to: normal-end
                    type: direct
                  - from: review
                    to: escalated
                    type: timeout
                  - from: escalated
                    to: timeout-end
                """);

        ProcessInstance instance = engine.start("bt-manual", Map.of());
        String instanceId = instance.getId();

        // Get the review task
        List<Task> reviewTasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instanceId) && t.isPending() && t.getNodeId().equals("review"))
                .toList();
        assertThat(reviewTasks).hasSize(1);

        // Manually complete the task
        engine.completeTask(reviewTasks.get(0).getId(), Map.of("approved", true), "同意");

        // Execution should go to normal-end (direct), NOT escalated (timeout)
        List<Execution> currentExecs = executionStore.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId) && !e.isCompleted()).toList();
        // After reaching endEvent, the flow should be COMPLETED
        ProcessInstance afterComplete = instanceStore.get(instanceId);
        // If it went to normal-end → COMPLETED; if it went to escalated → still RUNNING with a task
        assertThat(afterComplete.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }
}

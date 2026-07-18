package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CallActivityIntegrationTest {

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

        // Deploy child process first (parent depends on it)
        ProcessDefinition child = engine.deploy(new java.io.File(
                "src/test/resources/call-activity-child.yaml"));
        assertThat(child.getId()).isEqualTo("child-approval");

        // Deploy parent process
        ProcessDefinition parent = engine.deploy(new java.io.File(
                "src/test/resources/call-activity-parent.yaml"));
        assertThat(parent.getId()).isEqualTo("parent-main");
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
            public List<ProcessDefinition> findAllVersions(String id) {
                return processStore.values().stream()
                        .filter(d -> d.getId().equals(id))
                        .toList();
            }
        };
    }

    private com.github.wf.spi.InstanceRepository createInstanceRepo() {
        return new com.github.wf.spi.InstanceRepository() {
            public void save(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instanceStore.get(id); }
            public void update(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) {
                return instanceStore.values().stream()
                        .filter(i -> d.equals(i.getDefinitionId()))
                        .toList();
            }
            public List<ProcessInstance> findAll() {
                return new ArrayList<>(instanceStore.values());
            }
            public void saveExecution(Execution e) { executionStore.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executionStore.get(id); }
            public List<Execution> findActiveExecutions(String i) {
                return executionStore.values().stream()
                        .filter(e -> e.getInstanceId().equals(i) && !e.isCompleted())
                        .toList();
            }
            public List<Execution> findExecutionsByParentId(String p) {
                return executionStore.values().stream()
                        .filter(e -> p.equals(e.getParentExecutionId()))
                        .toList();
            }
            public void updateExecution(Execution e) { executionStore.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) { historyStore.add(h); }
            public List<HistoricActivity> findHistory(String i) {
                return historyStore.stream()
                        .filter(h -> h.getInstanceId().equals(i))
                        .toList();
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
    void testCallActivityStartsChildAndParentWaits() {
        // Start parent with applicant variable
        ProcessInstance parentInst = engine.start("parent-main",
                Map.of("applicant", "zhangsan"));
        assertThat(parentInst).isNotNull();
        assertThat(parentInst.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        // Parent should be WAITING at the callActivity; child should be RUNNING
        List<ProcessInstance> allInstances = engine.instanceRepository.findAll();
        assertThat(allInstances).hasSize(2); // parent + child

        ProcessInstance childInst = allInstances.stream()
                .filter(i -> i.getParentInstanceId() != null)
                .findFirst().orElseThrow();
        assertThat(childInst.getParentInstanceId()).isEqualTo(parentInst.getId());
        assertThat(childInst.getDefinitionId()).isEqualTo("child-approval");

        // Child should have the mapped variable: applicant → user
        assertThat(childInst.getVariable("user")).isEqualTo("zhangsan");

        // Find the child's pending task (created by UserTaskRunner)
        List<Task> tasks = engine.queryTasks(
                engine.taskQuery().instanceId(childInst.getId()));
        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getAssignee()).isEqualTo("zhangsan");

        // Complete the child task with the result variable
        engine.completeTask(task.getId(), Map.of("result", "approved"), "审批通过");

        // Child should now be COMPLETED
        childInst = engine.instanceRepository.findById(childInst.getId());
        assertThat(childInst.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // Parent should have resumed and completed
        parentInst = engine.instanceRepository.findById(parentInst.getId());
        assertThat(parentInst).isNotNull();
        assertThat(parentInst.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // Verify variable was written back: result → approvalResult
        assertThat(parentInst.getVariable("approvalResult")).isEqualTo("approved");
    }

    @Test
    void testCallActivityWithNonExistentDefinition() {
        // Deploy a bad parent that references a non-existent child
        engine.deploy("""
                id: bad-parent
                name: Bad Parent
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call-bad
                    type: callActivity
                    calledId: non-existent-process
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: call-bad
                  - from: call-bad
                    to: end
                """);

        // Start succeeds (exception is caught inside trigger loop and logged)
        ProcessInstance instance = engine.start("bad-parent", Map.of());
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        // The execution is stuck at call-bad — never advanced past the CallActivity
        List<Execution> activeExecs = engine.instanceRepository
                .findActiveExecutions(instance.getId());
        assertThat(activeExecs).hasSize(1);
        assertThat(activeExecs.get(0).getCurrentNodeId()).isEqualTo("call-bad");

        // No child instance was created
        List<ProcessInstance> all = engine.instanceRepository.findAll();
        assertThat(all).hasSize(1); // only the bad parent, no child
    }
}

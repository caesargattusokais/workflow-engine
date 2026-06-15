package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineDeployStartTest {

    private WorkflowEngine engine;
    private final Map<String, ProcessDefinition> processStore = new HashMap<>();
    private final Map<String, ProcessInstance> instanceStore = new HashMap<>();
    private final Map<String, Execution> executionStore = new HashMap<>();
    private final Map<String, com.github.wf.task.Task> taskStore = new HashMap<>();
    private final Map<String, Integer> latestVersion = new HashMap<>();

    @BeforeEach
    void setUp() {
        // Inline repos
        com.github.wf.spi.ProcessRepository processRepo = new com.github.wf.spi.ProcessRepository() {
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

        com.github.wf.spi.InstanceRepository instanceRepo = new com.github.wf.spi.InstanceRepository() {
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
            public List<Execution> findExecutionsByParentId(String p) { return List.of(); }
            public void updateExecution(Execution e) { executionStore.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) {}
            public List<HistoricActivity> findHistory(String i) { return List.of(); }
        };

        com.github.wf.spi.TaskRepository taskRepo = new com.github.wf.spi.TaskRepository() {
            public void save(com.github.wf.task.Task t) { taskStore.put(t.getId(), t); }
            public com.github.wf.task.Task findById(String id) { return taskStore.get(id); }
            public void update(com.github.wf.task.Task t) { taskStore.put(t.getId(), t); }
            public List<com.github.wf.task.Task> query(com.github.wf.task.TaskQuery q) {
                return taskStore.values().stream().filter(q::matches).toList();
            }
        };

        engine = WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

    @Test
    void deploysAndStartsSimpleWorkflow() {
        String yaml = """
                id: simple
                name: 简单流程
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: task1
                    type: userTask
                    name: 审批
                    assignee: "审批人"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: task1
                  - from: task1
                    to: end
                """;

        ProcessDefinition def = engine.deploy(yaml);
        assertThat(def.getId()).isEqualTo("simple");
        assertThat(latestVersion.get("simple")).isEqualTo(1);

        ProcessInstance instance = engine.start("simple", Map.of("initiator", "张三"));
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(instance.getVariables()).containsEntry("initiator", "张三");

        List<com.github.wf.task.Task> tasks = taskStore.values().stream()
                .filter(t -> t.getInstanceId().equals(instance.getId())).toList();
        assertThat(tasks).hasSize(1);
    }

    @Test
    void startUnknownDefinitionThrows() {
        assertThatThrownBy(() -> engine.start("nonexistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.model.node.FormField;
import com.github.wf.model.node.UserTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests for userTask completion forms: parsing + server-side required enforcement. */
class UserTaskFormTest {

    private WorkflowEngine engine;
    private final Map<String, ProcessDefinition> processStore = new HashMap<>();
    private final Map<String, ProcessInstance> instanceStore = new HashMap<>();
    private final Map<String, Execution> executionStore = new HashMap<>();
    private final Map<String, com.github.wf.task.Task> taskStore = new HashMap<>();
    private final Map<String, Integer> latestVersion = new HashMap<>();

    private static final String YAML = """
            id: form-flow
            name: 表单流程
            version: 1
            nodes:
              - id: start
                type: startEvent
              - id: apply
                type: userTask
                name: 提交报销单
                assignee: applicant
                formFields:
                  - key: amountText
                    label: 报销说明
                    type: textarea
                    required: true
                    placeholder: 请输入金额
                  - key: category
                    label: 费用类型
                    type: select
                    required: false
                    options: [差旅, 办公, 招待]
              - id: end
                type: endEvent
            transitions:
              - from: start
                to: apply
              - from: apply
                to: end
            """;

    @BeforeEach
    void setUp() {
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
            public ProcessDefinition findByIdAndVersion(String id, int version) { return processStore.get(id + ":" + version); }
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

    private com.github.wf.task.Task pendingTask() {
        return taskStore.values().stream()
                .filter(t -> t.getStatus() == com.github.wf.task.TaskStatus.PENDING)
                .findFirst().orElseThrow();
    }

    @Test
    void parsesFormFieldsFromYaml() {
        ProcessDefinition def = engine.deploy(YAML);
        Node node = def.getNode("apply");
        assertThat(node).isInstanceOf(UserTask.class);
        List<FormField> fields = ((UserTask) node).getFormFields();
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).getKey()).isEqualTo("amountText");
        assertThat(fields.get(0).getLabel()).isEqualTo("报销说明");
        assertThat(fields.get(0).getType()).isEqualTo("textarea");
        assertThat(fields.get(0).isRequired()).isTrue();
        assertThat(fields.get(1).isRequired()).isFalse();
        assertThat(fields.get(1).getOptions()).containsExactly("差旅", "办公", "招待");
    }

    @Test
    void exposesFormFieldsViaEngine() {
        engine.deploy(YAML);
        ProcessInstance inst = engine.start("form-flow", Map.of("applicant", "zhangsan"));
        com.github.wf.task.Task task = pendingTask();
        List<FormField> fields = engine.getTaskFormFields(task.getId());
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).getKey()).isEqualTo("amountText");
    }

    @Test
    void rejectsCompletionWhenRequiredFieldMissing() {
        engine.deploy(YAML);
        ProcessInstance inst = engine.start("form-flow", Map.of("applicant", "zhangsan"));
        com.github.wf.task.Task task = pendingTask();

        assertThatThrownBy(() -> engine.completeTask(task.getId(), Map.of(), "提交"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("报销说明");

        // Task must remain pending after a rejected completion
        assertThat(taskStore.get(task.getId()).getStatus()).isEqualTo(com.github.wf.task.TaskStatus.PENDING);
    }

    @Test
    void rejectsCompletionWhenRequiredFieldBlank() {
        engine.deploy(YAML);
        engine.start("form-flow", Map.of("applicant", "zhangsan"));
        com.github.wf.task.Task task = pendingTask();

        assertThatThrownBy(() -> engine.completeTask(task.getId(), Map.of("amountText", "   "), "提交"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("报销说明");
    }

    @Test
    void acceptsCompletionWithRequiredFieldAndWritesVariable() {
        engine.deploy(YAML);
        ProcessInstance inst = engine.start("form-flow", Map.of("applicant", "zhangsan"));
        com.github.wf.task.Task task = pendingTask();

        engine.completeTask(task.getId(), Map.of("amountText", "差旅费一共 2300 元"), "提交");

        assertThat(taskStore.get(task.getId()).getStatus()).isEqualTo(com.github.wf.task.TaskStatus.COMPLETED);
        assertThat(instanceStore.get(inst.getId()).getVariable("amountText")).isEqualTo("差旅费一共 2300 元");
    }

    @Test
    void userTaskWithoutFormAllowsEmptyCompletion() {
        String yaml = """
                id: no-form
                name: 无表单
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: t
                    type: userTask
                    name: 审批
                    assignee: manager
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: t
                  - from: t
                    to: end
                """;
        engine.deploy(yaml);
        engine.start("no-form", Map.of());
        com.github.wf.task.Task task = pendingTask();
        engine.completeTask(task.getId(), Map.of(), "同意");
        assertThat(taskStore.get(task.getId()).getStatus()).isEqualTo(com.github.wf.task.TaskStatus.COMPLETED);
    }
}

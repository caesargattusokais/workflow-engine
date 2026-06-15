package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.UserTask;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class UserTaskRunnerTest {

    // Use simple inline implementations instead of importing from memory module
    private com.github.wf.spi.InstanceRepository instanceRepo;
    private com.github.wf.spi.TaskRepository taskRepo;
    private SpelExpressionEvaluator exprEval;
    private ProcessDefinition def;

    @BeforeEach
    void setUp() {
        // Simple in-memory stores using HashMap
        final java.util.Map<String, com.github.wf.model.ProcessInstance> instances = new java.util.HashMap<>();
        final java.util.Map<String, Execution> executions = new java.util.HashMap<>();
        final java.util.Map<String, Task> tasks = new java.util.HashMap<>();

        instanceRepo = new com.github.wf.spi.InstanceRepository() {
            public void save(ProcessInstance i) { instances.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instances.get(id); }
            public void update(ProcessInstance i) { instances.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) { return List.of(); }
            public void saveExecution(Execution e) { executions.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executions.get(id); }
            public List<Execution> findActiveExecutions(String i) { return List.of(); }
            public List<Execution> findExecutionsByParentId(String p) { return List.of(); }
            public void updateExecution(Execution e) { executions.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) {}
            public List<HistoricActivity> findHistory(String i) { return List.of(); }
        };

        taskRepo = new com.github.wf.spi.TaskRepository() {
            public void save(Task t) { tasks.put(t.getId(), t); }
            public Task findById(String id) { return tasks.get(id); }
            public void update(Task t) { tasks.put(t.getId(), t); }
            public List<Task> query(TaskQuery q) {
                return tasks.values().stream().filter(q::matches).toList();
            }
        };

        exprEval = new SpelExpressionEvaluator();

        ProcessInstance instance = new ProcessInstance("inst-1", "wf",
                Map.of("applicant", "张三"));
        instanceRepo.save(instance);

        def = new ProcessDefinition("wf", "Test", 1,
                List.of(new UserTask("t1", "审批", "${applicant}",
                        List.of("manager"), null, null)),
                List.of());
    }

    @Test
    void createsTaskWhenFirstEntered() {
        Execution exec = new Execution("e1", "inst-1", "t1");
        instanceRepo.saveExecution(exec);

        UserTaskRunner runner = new UserTaskRunner(taskRepo);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        boolean advanced = runner.run(def.getNode("t1"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.isWaiting()).isTrue();

        List<Task> tasks = taskRepo.query(new TaskQuery().instanceId("inst-1"));
        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getNodeId()).isEqualTo("t1");
        assertThat(task.getAssignee()).isEqualTo("张三");
        assertThat(task.getCandidateGroups()).contains("manager");
    }

    @Test
    void skipsWhenTaskAlreadyExists() {
        Execution exec = new Execution("e1", "inst-1", "t1");
        instanceRepo.saveExecution(exec);

        UserTaskRunner runner = new UserTaskRunner(taskRepo);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        runner.run(def.getNode("t1"), ctx);
        exec.setStatus(ExecutionStatus.ACTIVE);
        boolean advanced = runner.run(def.getNode("t1"), ctx);

        assertThat(advanced).isFalse();
        List<Task> tasks = taskRepo.query(new TaskQuery().instanceId("inst-1"));
        assertThat(tasks).hasSize(1);
    }
}

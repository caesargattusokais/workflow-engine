package com.github.wf.engine;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineBuilderTest {

    private static final ProcessRepository STUB_PROCESS = new ProcessRepository() {
        public void save(ProcessDefinition d) {}
        public ProcessDefinition findById(String id) { return null; }
        public ProcessDefinition findLatestById(String id) { return null; }
        public List<ProcessDefinition> findAllVersions(String id) { return List.of(); }
    };

    private static final InstanceRepository STUB_INSTANCE = new InstanceRepository() {
        public void save(ProcessInstance i) {}
        public ProcessInstance findById(String id) { return null; }
        public void update(ProcessInstance i) {}
        public List<ProcessInstance> findByDefinitionId(String d) { return List.of(); }
        public void saveExecution(Execution e) {}
        public Execution findExecutionById(String id) { return null; }
        public List<Execution> findActiveExecutions(String i) { return List.of(); }
        public List<Execution> findExecutionsByParentId(String p) { return List.of(); }
        public void updateExecution(Execution e) {}
        public void saveHistoricActivity(HistoricActivity h) {}
        public List<HistoricActivity> findHistory(String i) { return List.of(); }
    };

    private static final TaskRepository STUB_TASK = new TaskRepository() {
        public void save(Task t) {}
        public Task findById(String id) { return null; }
        public void update(Task t) {}
        public List<Task> query(TaskQuery q) { return List.of(); }
    };

    @Test
    void buildsWithRequiredDependencies() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(STUB_PROCESS)
                .instanceRepository(STUB_INSTANCE)
                .taskRepository(STUB_TASK)
                .build();
        assertThat(engine).isNotNull();
    }

    @Test
    void requiresProcessRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .instanceRepository(STUB_INSTANCE).taskRepository(STUB_TASK).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresInstanceRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .processRepository(STUB_PROCESS).taskRepository(STUB_TASK).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresTaskRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .processRepository(STUB_PROCESS).instanceRepository(STUB_INSTANCE).build())
                .isInstanceOf(NullPointerException.class);
    }
}

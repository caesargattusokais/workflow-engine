package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRepositoryTest {

    private InMemoryProcessRepository processRepo;
    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;

    @BeforeEach
    void setUp() {
        processRepo = new InMemoryProcessRepository();
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();
    }

    @Test
    void processRepositoryFindsLatestVersion() {
        ProcessDefinition v1 = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("s"), new EndEvent("e")),
                List.of(Transition.direct("s", "e")));
        ProcessDefinition v2 = new ProcessDefinition("wf", "Test", 2,
                List.of(new StartEvent("s"), new EndEvent("e")),
                List.of(Transition.direct("s", "e")));
        processRepo.save(v1);
        processRepo.save(v2);
        assertThat(processRepo.findLatestById("wf").getVersion()).isEqualTo(2);
    }

    @Test
    void instanceRepositoryFindsActiveExecutions() {
        Execution e1 = new Execution("e1", "inst-1", "node-a", null);
        Execution e2 = new Execution("e2", "inst-1", "node-b", null);
        e2.setStatus(ExecutionStatus.COMPLETED);
        Execution e3 = new Execution("e3", "inst-2", "node-c", null);
        instanceRepo.saveExecution(e1);
        instanceRepo.saveExecution(e2);
        instanceRepo.saveExecution(e3);
        List<Execution> active = instanceRepo.findActiveExecutions("inst-1");
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo("e1");
    }

    @Test
    void instanceRepositoryFindsChildExecutions() {
        Execution parent = new Execution("p1", "inst-1", "gw", null);
        Execution child1 = new Execution("c1", "inst-1", "task-1", "p1");
        Execution child2 = new Execution("c2", "inst-1", "task-2", "p1");
        instanceRepo.saveExecution(parent);
        instanceRepo.saveExecution(child1);
        instanceRepo.saveExecution(child2);
        assertThat(instanceRepo.findExecutionsByParentId("p1")).hasSize(2);
    }

    @Test
    void taskRepositoryQueriesByAssignee() {
        Task t1 = new Task("t1", "inst-1", "node-1");
        t1.setAssignee("张三");
        Task t2 = new Task("t2", "inst-1", "node-2");
        t2.setAssignee("李四");
        taskRepo.save(t1);
        taskRepo.save(t2);
        List<Task> result = taskRepo.query(new TaskQuery().assignee("张三"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    @Test
    void historyIsRecorded() {
        HistoricActivity ha = HistoricActivity.nodeEnter("inst-1", "start", "开始", NodeType.START_EVENT);
        instanceRepo.saveHistoricActivity(ha);
        List<HistoricActivity> history = instanceRepo.findHistory("inst-1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo("enter");
    }
}

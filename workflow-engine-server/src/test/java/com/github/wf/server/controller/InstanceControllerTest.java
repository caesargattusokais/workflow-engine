package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.*;
import com.github.wf.server.dto.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;

class InstanceControllerTest {
    private WorkflowEngine engine;
    private DefinitionController defCtrl;
    private InstanceController instCtrl;
    private TaskController taskCtrl;

    @BeforeEach
    void setUp() {
        engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        defCtrl = new DefinitionController(engine);
        instCtrl = new InstanceController(engine);
        taskCtrl = new TaskController(engine);
    }

    @Test
    void fullFlowStartToListToComplete() {
        DeployRequest dr = new DeployRequest();
        dr.setYaml("""
                id: f
                version: 1
                nodes:
                  - id: s
                    type: startEvent
                  - id: t
                    type: userTask
                    name: 审批
                    assignee: "user1"
                  - id: e
                    type: endEvent
                transitions:
                  - from: s
                    to: t
                  - from: t
                    to: e
                """);
        defCtrl.deploy("test-user", dr);

        StartInstanceRequest sir = new StartInstanceRequest();
        sir.setDefinitionId("f");
        sir.setVariables(Map.of("x", 1));
        var inst = instCtrl.start("test-user", sir);
        assertThat(inst.getStatus()).isEqualTo("RUNNING");

        var tasks = taskCtrl.list("user1", null, inst.getId(), null);
        assertThat(tasks).hasSize(1);

        CompleteTaskRequest ctr = new CompleteTaskRequest();
        ctr.setComment("ok");
        taskCtrl.complete(tasks.get(0).get("id").toString(), ctr);

        var updated = instCtrl.get(inst.getId());
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
    }
}

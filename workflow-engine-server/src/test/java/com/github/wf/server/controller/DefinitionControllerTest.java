package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.server.dto.DeployRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DefinitionControllerTest {

    @Test
    void deployAndGetGraph() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        DefinitionController ctrl = new DefinitionController(engine);

        DeployRequest req = new DeployRequest();
        req.setYaml("""
                id: test
                version: 1
                nodes:
                  - id: s
                    type: startEvent
                  - id: e
                    type: endEvent
                transitions:
                  - from: s
                    to: e
                """);
        ctrl.deploy("test-user", req);

        GraphResponse graph = ctrl.graph("test-user", "test",1);
        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getEdges()).hasSize(1);
        assertThat(graph.getNodes().get(0).getType()).isEqualTo("startEvent");
        assertThat(graph.getNodes().get(1).getType()).isEqualTo("endEvent");
    }
}

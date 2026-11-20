package com.github.wf.mcp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowToolsTest {

    private final EngineClient engine = mock(EngineClient.class);
    private final WorkflowTools tools = new WorkflowTools(engine);

    @Test
    void listDefinitionsAppliesDefaultPaging() {
        when(engine.get(any(String.class))).thenReturn(Map.of());
        tools.listDefinitions(null, null);
        verify(engine).get("/definitions?page=1&size=20");
        tools.listDefinitions(0, 500);
        verify(engine).get("/definitions?page=1&size=500");
    }

    @Test
    void deployPostsYaml() {
        when(engine.post(eq("/definitions"), any())).thenReturn(Map.of("id", "d1"));
        Object out = tools.deployWorkflow("id: f\nnodes: []");
        assertThat(out).isEqualTo(Map.of("id", "d1"));
        verify(engine).post("/definitions", Map.of("yaml", "id: f\nnodes: []"));
    }

    @Test
    void startInstanceDefaultsVariablesToEmpty() {
        when(engine.post(any(String.class), any())).thenReturn(Map.of("id", "i1"));
        tools.startInstance("d1", null);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(engine).post(eq("/instances"), body.capture());
        assertThat(body.getValue()).isEqualTo(Map.of("definitionId", "d1", "variables", Map.of()));
    }

    @Test
    void listInstancesEncodesFilters() {
        when(engine.get(any(String.class))).thenReturn(java.util.List.of());
        tools.listInstances("def x", "RUNNING", null, null);
        verify(engine).get("/instances?page=1&size=20&definitionId=def+x&status=RUNNING");
        tools.listInstances(null, null, 2, 50);
        verify(engine).get("/instances?page=2&size=50");
    }

    @Test
    void listTasksSkipsBlankFilters() {
        when(engine.get(any(String.class))).thenReturn(java.util.List.of());
        tools.listTasks("u1", " ", null);
        verify(engine).get("/tasks?assignee=u1");
    }

    @Test
    void completeTaskSendsVariablesAndComment() {
        when(engine.post(any(String.class), any())).thenReturn(Map.of("ok", true));
        tools.completeTask("t1", Map.of("amountText", "2300 元"), "approved");
        verify(engine).post("/tasks/t1/complete",
                Map.of("variables", Map.of("amountText", "2300 元"), "comment", "approved"));
        tools.completeTask("t1", null, null);
        verify(engine).post("/tasks/t1/complete",
                Map.of("variables", Map.of(), "comment", ""));
    }

    @Test
    void diagnosePostsInstanceId() {
        when(engine.post(any(String.class), any())).thenReturn(Map.of("summary", "x"));
        tools.diagnoseInstance("i9");
        verify(engine).post("/ai/diagnose", Map.of("instanceId", "i9"));
    }

    @Test
    void engineErrorsSurfaceAsIllegalStateWithMessage() {
        when(engine.get(any(String.class)))
                .thenThrow(new IllegalStateException("GET /instances/nope failed (404): {\"error\":\"not found\"}"));
        assertThatThrownBy(() -> tools.getInstance("nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("404");
    }

    @Test
    void deleteReturnsConfirmation() {
        when(engine.delete(any(String.class))).thenReturn("deleted: /instances/i1");
        assertThat(tools.deleteInstance("i1")).isEqualTo("deleted: /instances/i1");
    }
}

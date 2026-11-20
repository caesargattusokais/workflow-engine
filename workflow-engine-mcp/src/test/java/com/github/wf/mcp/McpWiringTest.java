package com.github.wf.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the MCP wiring: all workflow tools are registered as callbacks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpWiringTest {

    @Autowired
    private ToolCallbackProvider provider;

    @Test
    void registersAllWorkflowTools() {
        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).extracting(ToolCallback::getToolDefinition)
                .extracting(def -> def.name())
                .containsExactlyInAnyOrder(
                        "list_workflow_definitions",
                        "deploy_workflow",
                        "start_workflow_instance",
                        "list_workflow_instances",
                        "get_workflow_instance",
                        "get_workflow_history",
                        "delete_instance",
                        "list_tasks",
                        "complete_task",
                        "diagnose_instance");
    }
}

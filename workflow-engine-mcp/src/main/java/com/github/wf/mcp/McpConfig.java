package com.github.wf.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link WorkflowTools} @Tool methods with the MCP server. */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider workflowToolCallbackProvider(WorkflowTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}

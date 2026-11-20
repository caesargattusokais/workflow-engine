package com.github.wf.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP server application. It does NOT embed the engine — it talks
 * to a running workflow-engine-server over its REST API, so it can be deployed
 * next to the engine (or anywhere that can reach it) without sharing a JVM,
 * a database, or the engine's locks.
 */
@SpringBootApplication
public class McpApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpApplication.class, args);
    }
}

package com.github.wf.examples;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;

import java.util.Map;

/**
 * Demonstrates SPI-based ServiceTaskHandler discovery via ServiceLoader.
 *
 * ApprovalHandler is registered in META-INF/services/com.github.wf.ext.ServiceTaskHandler.
 * The engine finds it automatically — no engine.registerServiceHandler() call needed.
 * YAML can use either the full class name or simple class name.
 */
public class SpiExample {

    public static void main(String[] args) {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        engine.setProcessParser(new YamlProcessParser());

        // No registerServiceHandler() call — handler discovered via SPI

        System.out.println("=== SPI 示例: ServiceLoader 自动发现 Handler ===\n");

        // Deploy a flow using the SPI handler — note simple class name
        engine.deploy("""
                id: spi-demo
                name: SPI审批示例
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: approve
                    type: serviceTask
                    name: 审批
                    handlerClass: ApprovalHandler
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: approve
                  - from: approve
                    to: end
                """);

        System.out.println("启动实例 (amount=3000 → approved)...");
        var inst = engine.start("spi-demo", Map.of("amount", 3000));
        System.out.println("状态: " + inst.getStatus() + "\n");

        // Demonstrate that FQN also works
        engine.deploy("""
                id: spi-demo2
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: approve
                    type: serviceTask
                    name: 审批
                    handlerClass: com.github.wf.examples.ApprovalHandler
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: approve
                  - from: approve
                    to: end
                """);

        System.out.println("启动实例 (amount=8000 → pending_review)...");
        var inst2 = engine.start("spi-demo2", Map.of("amount", 8000));
        System.out.println("状态: " + inst2.getStatus());

        System.out.println("\n=== SPI 示例完成 ===");
    }
}

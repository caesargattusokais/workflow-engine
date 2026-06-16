package com.github.wf.examples;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;

import java.util.Map;

/**
 * Demonstrates ServiceTask with retry, result routing, and exception routing.
 *
 * Flow: start -> risk-check (HTTP mock) -> approved | manual-review | system-error -> end
 */
public class ServiceTaskRoutingDemo {

    private static int callCount = 0;

    public static void main(String[] args) {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        engine.setProcessParser(new YamlProcessParser());

        // Register a handler that simulates calling an external API
        // Fails twice (timeout), then succeeds with a risk result
        engine.registerServiceHandler("com.demo.RiskCheckHandler", vars -> {
            callCount++;
            System.out.println("  >>> API 调用 #" + callCount);

            if (callCount == 1) {
                System.out.println("  >>> 模拟超时, 触发重试...");
                throw new RuntimeException("SocketTimeoutException: connect timed out");
            }
            if (callCount == 2) {
                System.out.println("  >>> 模拟连接异常, 触发重试...");
                throw new RuntimeException("IOException: connection reset");
            }
            // Success
            int amount = (int) vars.getOrDefault("amount", 100);
            String risk = amount > 5000 ? "HIGH" : "LOW";
            System.out.println("  >>> 调用成功, 返回: risk=" + risk);
            return Map.of("risk", risk, "score", 85);
        });

        // Deploy flow with retry + result routing
        var def = engine.deploy("""
                id: risk-demo
                name: 风控审批流程
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: risk-check
                    type: serviceTask
                    name: 风控检查
                    handlerClass: "com.demo.RiskCheckHandler"
                    retry:
                      maxAttempts: 3
                      delayMs: 0
                      backoffMultiplier: 1
                      retryOn:
                        - expr: "exception.message.contains('SocketTimeout')"
                        - expr: "exception.message.contains('IOException')"
                    resultRouting:
                      - expr: "result['risk'] == 'LOW'"
                        to: approved
                      - expr: "result['risk'] == 'HIGH'"
                        to: manual-review
                    exceptionRouting:
                      - default: true
                        to: system-error
                  - id: approved
                    type: userTask
                    name: 自动审批通过通知
                    assignee: "admin"
                  - id: manual-review
                    type: userTask
                    name: 人工复核
                    assignee: "reviewer"
                  - id: system-error
                    type: endEvent
                    name: 系统异常
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: risk-check
                  - from: approved
                    to: end
                  - from: manual-review
                    to: end
                """);
        System.out.println("=== 部署: " + def.getName() + " ===\n");

        // --- Test 1: Low risk (amount=100, should route to approved) ---
        System.out.println("--- Test 1: amount=100, risk=LOW ---");
        callCount = 0;

        var instance = engine.start("risk-demo", Map.of("amount", 100, "applicant", "张三"));

        System.out.println("实例状态: " + instance.getStatus());
        var tasks = engine.queryTasks(engine.taskQuery().instanceId(instance.getId()));
        if (!tasks.isEmpty()) {
            var t = tasks.get(0);
            System.out.println("待办节点: " + t.getNodeId() + " -> " + t.getAssignee());
            engine.completeTask(t.getId(), Map.of(), "done");
        }
        System.out.println("最终状态: " + instance.getStatus());
        System.out.println("历史: " + engine.history(instance.getId()).size() + " 条");

        // --- Test 2: Suspend & resume (handler always throws, no retry) ---
        System.out.println("\n--- Test 2: Suspend & Resume ---");

        engine.deploy("""
                id: suspend-demo
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    name: 不稳定接口
                    handlerClass: "com.demo.BrokenHandler"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                  - from: call
                    to: end
                """);

        engine.registerServiceHandler("com.demo.BrokenHandler", vars -> {
            System.out.println("  >>> 接口调用失败!");
            throw new RuntimeException("unexpected crash");
        });

        var inst2 = engine.start("suspend-demo", Map.of());
        System.out.println("状态应为 SUSPENDED: " + inst2.getStatus());
        System.out.println("挂起原因: " + inst2.getVariable("_suspendReason"));

        // Fix the handler and resume
        engine.registerServiceHandler("com.demo.BrokenHandler", vars -> {
            System.out.println("  >>> 修复后调用成功!");
            return Map.of("fixed", true);
        });
        engine.resume(inst2.getId());
        System.out.println("恢复后状态: " + engine.instanceRepository.findById(inst2.getId()).getStatus());

        System.out.println("\n=== Demo 完成 ===");
    }
}

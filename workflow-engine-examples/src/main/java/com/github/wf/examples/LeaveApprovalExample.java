package com.github.wf.examples;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import com.github.wf.task.Task;

import java.util.List;
import java.util.Map;

/**
 * Demonstrates the full workflow engine lifecycle:
 * build → deploy → start → complete tasks → query history.
 */
public class LeaveApprovalExample {

    public static void main(String[] args) {
        // 1. Build engine with in-memory repositories
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        engine.setProcessParser(new YamlProcessParser());

        // 2. Deploy process definition
        ProcessDefinition def = engine.deploy("""
                id: leave-approval
                name: 请假审批流程
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: apply
                    type: userTask
                    name: 提交请假申请
                    assignee: "${applicant}"
                  - id: gateway
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 3"
                        to: manager-approve
                      - default: true
                        to: department-manager
                  - id: manager-approve
                    type: userTask
                    name: 总经理审批
                    candidateGroups: ["manager"]
                  - id: department-manager
                    type: userTask
                    name: 部门经理审批
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: apply
                  - from: apply
                    to: gateway
                  - from: manager-approve
                    to: end
                  - from: department-manager
                    to: end
                """);
        System.out.println("部署流程: " + def.getName() + " v" + def.getVersion());

        // 3. Start instance — 5 days leave, goes to manager
        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "张三", "days", 5));
        System.out.println("启动实例: " + instance.getId());
        System.out.println("状态: " + instance.getStatus());

        // 4. Applicant's pending task
        List<Task> applicantTasks = engine.queryTasks(
                engine.taskQuery().assignee("张三"));
        System.out.println("张三的待办: " + applicantTasks.size() + " 个");

        if (!applicantTasks.isEmpty()) {
            Task task = applicantTasks.get(0);
            System.out.println("  节点: " + task.getNodeId() + " -> " + task.getAssignee());

            // 5. Complete task
            engine.completeTask(task.getId(), Map.of("reason", "看病"), "已提交");
            System.out.println("  任务完成: " + task.getId());
        }

        // 6. Check manager tasks
        List<Task> managerTasks = engine.queryTasks(
                engine.taskQuery().candidateGroup("manager"));
        System.out.println("经理待办: " + managerTasks.size() + " 个");
        if (!managerTasks.isEmpty()) {
            Task mt = managerTasks.get(0);
            engine.completeTask(mt.getId(), Map.of("approved", true), "同意请假");
            System.out.println("  经理审批完成");
        }

        // 7. Check final status
        ProcessInstance finalInstance = engine.instanceRepository.findById(instance.getId());
        System.out.println("最终状态: " + finalInstance.getStatus());

        // 8. History
        List<HistoricActivity> history = engine.history(instance.getId());
        System.out.println("历史记录 (" + history.size() + " 条):");
        history.forEach(h -> System.out.println("  " + h.getAction() + " - " + h.getNodeName()));
    }
}

package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final WorkflowEngine engine;

    public DashboardController(WorkflowEngine engine) { this.engine = engine; }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader("X-User-Id") String userId) {
        List<ProcessInstance> all = engine.instanceRepository.findAll().stream()
                .filter(i -> userId.equals(i.getVariable("_userId"))).toList();

        long total = all.size();
        long running = all.stream().filter(i -> i.getStatus() == InstanceStatus.RUNNING).count();
        long completed = all.stream().filter(i -> i.getStatus() == InstanceStatus.COMPLETED).count();
        long suspended = all.stream().filter(i -> i.getStatus() == InstanceStatus.SUSPENDED).count();
        long terminated = all.stream().filter(i -> i.getStatus() == InstanceStatus.TERMINATED).count();

        // Average duration for completed instances
        double avgDuration = all.stream()
                .filter(i -> i.getCompletedAt() != null && i.getCreatedAt() != null)
                .mapToLong(i -> i.getCompletedAt().toEpochMilli() - i.getCreatedAt().toEpochMilli())
                .average().orElse(0);

        // Per-definition breakdown
        Map<String, Map<String, Long>> byDef = new LinkedHashMap<>();
        for (ProcessInstance i : all) {
            byDef.computeIfAbsent(i.getDefinitionId(), k -> new LinkedHashMap<>())
                 .merge(i.getStatus().name(), 1L, Long::sum);
        }

        // Approver workload — count tasks by assignee
        Map<String, Long> workload = new LinkedHashMap<>();
        List<Task> allTasks = engine.queryTasks(engine.taskQuery());
        allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.COMPLETED)
                .filter(t -> t.getAssignee() != null)
                .forEach(t -> workload.merge(t.getAssignee(), 1L, Long::sum));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("running", running);
        result.put("completed", completed);
        result.put("suspended", suspended);
        result.put("terminated", terminated);
        result.put("avgDurationMs", Math.round(avgDuration));
        result.put("byDefinition", byDef);
        result.put("workload", workload);
        return result;
    }

    @GetMapping("/timeline/{instanceId}")
    public List<Map<String, Object>> timeline(@PathVariable("instanceId") String instanceId) {
        List<HistoricActivity> history = engine.history(instanceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (HistoricActivity h : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", h.getNodeId());
            m.put("nodeName", h.getNodeName());
            m.put("action", h.getAction());
            m.put("executor", h.getExecutor());
            m.put("time", h.getTimestamp().toString());
            result.add(m);
        }
        return result;
    }
}

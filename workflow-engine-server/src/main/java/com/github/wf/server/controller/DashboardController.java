package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.DraftRepository;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final WorkflowEngine engine;
    private final DraftRepository draftRepo;

    public DashboardController(WorkflowEngine engine, DraftRepository draftRepo) {
        this.engine = engine;
        this.draftRepo = draftRepo;
    }

    /** List user's drafts for the dashboard sidebar */
    @GetMapping("/definitions")
    public List<Map<String, Object>> definitions(@RequestHeader("X-User-Id") String userId) {
        return draftRepo.listByUser(userId).stream()
                .map(d -> Map.of("id", d.get("id"), "name", d.get("name")))
                .toList();
    }

    /** Stats for a specific definition (scoped to the user who owns the draft) */
    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("definitionId") String definitionId) {
        InstanceStats stats = engine.instanceRepository.getStatsByDefinition(definitionId);

        // Workload: only count tasks belonging to instances of this definition
        Set<String> instanceIds = engine.instanceRepository.findByDefinitionId(definitionId)
                .stream().map(ProcessInstance::getId).collect(Collectors.toSet());
        Map<String, Long> workload = new LinkedHashMap<>();
        List<Task> allTasks = engine.queryTasks(engine.taskQuery());
        allTasks.stream()
                .filter(t -> instanceIds.contains(t.getInstanceId()))
                .filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.COMPLETED)
                .filter(t -> t.getAssignee() != null)
                .forEach(t -> workload.merge(t.getAssignee(), 1L, Long::sum));

        Map<String, Object> result = stats.toMap();
        result.put("workload", workload);
        return result;
    }

    @GetMapping("/timeline/{instanceId}")
    public List<Map<String, Object>> timeline(@PathVariable("instanceId") String instanceId) {
        List<HistoricActivity> history = engine.history(instanceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            HistoricActivity h = history.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", h.getNodeId());
            m.put("nodeName", h.getNodeName());
            m.put("action", h.getAction());
            m.put("time", h.getTimestamp().toString());
            if (i + 1 < history.size()) {
                long duration = history.get(i + 1).getTimestamp().toEpochMilli()
                        - h.getTimestamp().toEpochMilli();
                m.put("durationMs", duration);
            }
            result.add(m);
        }
        return result;
    }
}

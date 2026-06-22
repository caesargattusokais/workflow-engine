package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final WorkflowEngine engine;

    public DashboardController(WorkflowEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam("definitionId") String definitionId) {
        return engine.instanceRepository.getStatsByDefinition(definitionId).toMap();
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

package com.github.wf.server.ws;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.ProcessInstance;
import com.github.wf.server.controller.DefinitionController;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.task.TaskQuery;
import com.google.gson.Gson;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

/**
 * Reads instance state from the engine and builds WebSocket JSON messages.
 * Uses ObjectProvider&lt;WorkflowEngine&gt; to avoid CGLIB proxy issues with
 * the engine's final fields (instanceRepository, etc.).
 */
public class InstanceStateDataService {

    private static final Gson gson = new Gson();
    private final ObjectProvider<WorkflowEngine> engineProvider;

    public InstanceStateDataService(ObjectProvider<WorkflowEngine> engineProvider) {
        this.engineProvider = engineProvider;
    }

    private WorkflowEngine engine() { return engineProvider.getObject(); }

    /** Build full snapshot JSON for a newly connected client. */
    public String buildSnapshot(String instanceId) {
        WorkflowEngine engine = engine();
        ProcessInstance inst = engine.instanceRepository.findById(instanceId);
        if (inst == null) return null;
        GraphResponse graph = buildGraph(engine, inst);
        var tasks = engine.taskRepository.query(new TaskQuery().instanceId(instanceId));
        var history = engine.instanceRepository.findHistory(instanceId)
            .stream().map(h -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nodeId", h.getNodeId()); m.put("nodeName", h.getNodeName());
                m.put("action", h.getAction()); m.put("executor", h.getExecutor());
                return m;
            }).toList();

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "snapshot");
        msg.put("instance", instanceToMap(inst));
        msg.put("graph", graph);
        msg.put("tasks", tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId()); m.put("nodeId", t.getNodeId());
            m.put("assignee", t.getAssignee()); m.put("status", t.getStatus().name());
            return m;
        }).toList());
        msg.put("history", history);
        return gson.toJson(msg);
    }

    /** Build incremental update JSON after engine state change. */
    public String buildUpdate(String instanceId) {
        WorkflowEngine engine = engine();
        ProcessInstance inst = engine.instanceRepository.findById(instanceId);
        if (inst == null) return null;
        var tasks = engine.taskRepository.query(new TaskQuery().instanceId(instanceId));
        var history = engine.instanceRepository.findHistory(instanceId)
            .stream().map(h -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nodeId", h.getNodeId()); m.put("nodeName", h.getNodeName());
                m.put("action", h.getAction()); m.put("executor", h.getExecutor());
                return m;
            }).toList();

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "update");
        msg.put("instance", instanceToMap(inst));
        msg.put("tasks", tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId()); m.put("nodeId", t.getNodeId());
            m.put("assignee", t.getAssignee()); m.put("status", t.getStatus().name());
            return m;
        }).toList());
        msg.put("history", history);
        return gson.toJson(msg);
    }

    private GraphResponse buildGraph(WorkflowEngine engine, ProcessInstance inst) {
        try {
            var def = engine.processRepository.findLatestById(inst.getDefinitionId());
            if (def == null) return new GraphResponse(List.of(), List.of());
            return DefinitionController.graphFromDef(def, null);
        } catch (Exception e) {
            return new GraphResponse(List.of(), List.of());
        }
    }

    static Map<String, Object> instanceToMap(ProcessInstance inst) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inst.getId());
        m.put("definitionId", inst.getDefinitionId());
        m.put("definitionVersion", inst.getDefinitionVersion());
        m.put("status", inst.getStatus().name());
        m.put("activeNodeIds", new ArrayList<>(inst.getActiveNodeIds()));
        m.put("variables", inst.getVariables());
        return m;
    }
}

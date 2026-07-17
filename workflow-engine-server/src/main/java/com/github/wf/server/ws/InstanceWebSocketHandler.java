package com.github.wf.server.ws;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.ProcessInstance;
import com.github.wf.server.controller.DefinitionController;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for per-instance real-time state updates.
 * Clients connect to /ws/instance/{instanceId} and receive
 * a full snapshot on connect, then incremental updates on state changes.
 */
public class InstanceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InstanceWebSocketHandler.class);
    private static final Gson gson = new Gson();

    private final WorkflowEngine engine;

    /** instanceId → set of subscribed sessions */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** session → instanceId (for cleanup) */
    private final ConcurrentHashMap<String, String> sessionToInstance = new ConcurrentHashMap<>();

    public InstanceWebSocketHandler(WorkflowEngine engine) {
        this.engine = engine;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String instanceId = extractInstanceId(session);
        if (instanceId == null) {
            try { session.close(CloseStatus.BAD_DATA); } catch (IOException ignored) {}
            return;
        }
        sessions.computeIfAbsent(instanceId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToInstance.put(session.getId(), instanceId);
        log.debug("WS connect: instance={} session={}", instanceId, session.getId());
        sendSnapshot(session, instanceId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String instanceId = sessionToInstance.remove(session.getId());
        if (instanceId != null) {
            Set<WebSocketSession> set = sessions.get(instanceId);
            if (set != null) { set.remove(session); if (set.isEmpty()) sessions.remove(instanceId); }
            log.debug("WS disconnect: instance={} session={}", instanceId, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("WS error: session={} msg={}", session.getId(), ex.getMessage());
    }

    /** Called by notifier when engine state changes. Pushes an update to all subscribers. */
    public void pushUpdate(String instanceId) {
        Set<WebSocketSession> set = sessions.get(instanceId);
        if (set == null || set.isEmpty()) return;
        try {
            Map<String, Object> msg = buildUpdate(instanceId);
            if (msg == null) return;
            String json = gson.toJson(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : set) {
                try { s.sendMessage(tm); } catch (IOException e) { log.warn("WS send failed: {}", e.getMessage()); }
            }
        } catch (Exception e) {
            log.warn("pushUpdate error: instance={} msg={}", instanceId, e.getMessage());
        }
    }

    /** Send full snapshot on connect. */
    private void sendSnapshot(WebSocketSession session, String instanceId) {
        try {
            ProcessInstance inst = engine.instanceRepository.findById(instanceId);
            if (inst == null) return;
            GraphResponse graph = buildGraph(inst);
            List<Task> tasks = engine.taskRepository.query(new TaskQuery().instanceId(instanceId));
            List<Map<String, Object>> history = engine.instanceRepository.findHistory(instanceId)
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
            session.sendMessage(new TextMessage(gson.toJson(msg)));
        } catch (Exception e) {
            log.warn("sendSnapshot error: {}", e.getMessage());
        }
    }

    /** Build incremental update message. */
    private Map<String, Object> buildUpdate(String instanceId) {
        ProcessInstance inst = engine.instanceRepository.findById(instanceId);
        if (inst == null) return null;
        List<Task> tasks = engine.taskRepository.query(new TaskQuery().instanceId(instanceId));
        List<Map<String, Object>> history = engine.instanceRepository.findHistory(instanceId)
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
        return msg;
    }

    private GraphResponse buildGraph(ProcessInstance inst) {
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

    private static String extractInstanceId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        // path: /ws/instance/{instanceId}
        String[] parts = path.split("/");
        if (parts.length >= 4) return parts[3];
        // Also try query param
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.startsWith("instanceId=")) return query.substring(11);
        return null;
    }
}

package com.github.wf.server.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Pure WebSocket session manager — no engine dependency.
 * Manages per-instance session registry and broadcasts.
 */
public class InstanceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InstanceWebSocketHandler.class);

    /** instanceId → set of subscribed sessions */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    /** session → instanceId (for cleanup) */
    private final ConcurrentHashMap<String, String> sessionToInstance = new ConcurrentHashMap<>();

    /** Called when a client first connects — to send a snapshot. Set by the notifier. */
    private Consumer<WebSocketSession> onConnect;

    /** Called when state changes — to build an update message. Set by the notifier. */
    private Consumer<String> onStateChanged;

    public void setOnConnect(Consumer<WebSocketSession> onConnect) { this.onConnect = onConnect; }
    public void setOnStateChanged(Consumer<String> onStateChanged) { this.onStateChanged = onStateChanged; }

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
        if (onConnect != null) onConnect.accept(session);
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

    /** Broadcast a JSON message to all sessions subscribed to this instance. */
    public void broadcast(String instanceId, String jsonMessage) {
        Set<WebSocketSession> set = sessions.get(instanceId);
        if (set == null || set.isEmpty()) return;
        TextMessage tm = new TextMessage(jsonMessage);
        for (WebSocketSession s : set) {
            try { s.sendMessage(tm); } catch (IOException e) { log.warn("WS send failed: {}", e.getMessage()); }
        }
    }

    /** Send a JSON message to a single session (used for connect snapshot). */
    public void sendToSession(WebSocketSession session, String jsonMessage) {
        try { session.sendMessage(new TextMessage(jsonMessage)); } catch (IOException e) {
            log.warn("WS send snapshot failed: {}", e.getMessage());
        }
    }

    /** Does any client subscribe to this instance? */
    public boolean hasSubscribers(String instanceId) {
        Set<WebSocketSession> set = sessions.get(instanceId);
        return set != null && !set.isEmpty();
    }

    // ── Helpers ──

    private static String extractInstanceId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        if (parts.length >= 4) return parts[3];
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.startsWith("instanceId=")) return query.substring(11);
        return null;
    }
}

package com.github.wf.server.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcast channel for the monitor page. Not associated with a specific instance.
 * Clients connect to /ws/monitor and receive lightweight change notifications:
 * {"type":"changed","instanceId":"xxx","status":"RUNNING","activeNodeIds":[...]}
 */
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("Monitor WS connect: session={} total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("Monitor WS disconnect: session={} total={}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("Monitor WS error: session={} msg={}", session.getId(), ex.getMessage());
    }

    /** Broadcast a JSON message to all connected monitor clients. */
    public void broadcast(String jsonMessage) {
        if (sessions.isEmpty()) { log.debug("Monitor broadcast skipped — no sessions"); return; }
        log.info("Monitor broadcast: sessions={} msg={}", sessions.size(), jsonMessage);
        TextMessage tm = new TextMessage(jsonMessage);
        for (WebSocketSession s : sessions) {
            try { s.sendMessage(tm); } catch (IOException e) {
                log.warn("Monitor WS send failed: {}", e.getMessage());
            }
        }
    }
}

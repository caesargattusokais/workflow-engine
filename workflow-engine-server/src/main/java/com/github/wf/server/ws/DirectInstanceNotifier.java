package com.github.wf.server.ws;

import com.github.wf.engine.InstanceStateListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-Redis (single-node) mode: listens for engine state changes,
 * builds WS messages via InstanceStateDataService, and pushes via
 * InstanceWebSocketHandler.
 */
@Component
@Profile("!redis")
public class DirectInstanceNotifier implements InstanceStateListener {

    private static final Logger log = LoggerFactory.getLogger(DirectInstanceNotifier.class);

    private final InstanceWebSocketHandler wsHandler;
    private final MonitorWebSocketHandler monitorHandler;
    private final InstanceStateDataService dataService;

    public DirectInstanceNotifier(InstanceWebSocketHandler wsHandler,
                                  MonitorWebSocketHandler monitorHandler,
                                  InstanceStateDataService dataService) {
        this.wsHandler = wsHandler;
        this.monitorHandler = monitorHandler;
        this.dataService = dataService;
    }

    @PostConstruct
    void wireCallbacks() {
        wsHandler.setOnConnect(session -> {
            String instanceId = getInstanceId(session);
            if (instanceId != null) {
                try {
                    String json = dataService.buildSnapshot(instanceId);
                    if (json != null) wsHandler.sendToSession(session, json);
                } catch (Exception e) { log.warn("Snapshot error: {}", e.getMessage()); }
            }
        });
    }

    @Override
    public void onStateChanged(String instanceId) {
        // Always push to monitor (no subscriber check — monitor needs all)
        try {
            String monitorMsg = dataService.buildMonitorMessage(instanceId);
            if (monitorMsg != null) monitorHandler.broadcast(monitorMsg);
        } catch (Exception e) { log.warn("Monitor broadcast error: {}", e.getMessage()); }
        // Push detail update only if someone is watching this instance
        if (!wsHandler.hasSubscribers(instanceId)) return;
        try {
            String json = dataService.buildUpdate(instanceId);
            if (json != null) wsHandler.broadcast(instanceId, json);
        } catch (Exception e) {
            log.warn("State update error: instance={} msg={}", instanceId, e.getMessage());
        }
    }

    private static String getInstanceId(org.springframework.web.socket.WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }
}

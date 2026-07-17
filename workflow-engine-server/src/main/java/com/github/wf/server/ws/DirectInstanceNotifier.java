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
    private final InstanceStateDataService dataService;

    public DirectInstanceNotifier(InstanceWebSocketHandler wsHandler,
                                  InstanceStateDataService dataService) {
        this.wsHandler = wsHandler;
        this.dataService = dataService;
    }

    @PostConstruct
    void wireCallbacks() {
        // When a client connects, build snapshot and send
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

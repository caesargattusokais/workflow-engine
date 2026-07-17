package com.github.wf.server.ws;

import com.github.wf.engine.InstanceStateListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-Redis (single-node) mode: directly pushes state changes
 * to connected WebSocket clients in the same JVM.
 */
@Component
@Profile("!redis")
public class DirectInstanceNotifier implements InstanceStateListener {

    private final InstanceWebSocketHandler wsHandler;

    public DirectInstanceNotifier(InstanceWebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @Override
    public void onStateChanged(String instanceId) {
        wsHandler.pushUpdate(instanceId);
    }
}

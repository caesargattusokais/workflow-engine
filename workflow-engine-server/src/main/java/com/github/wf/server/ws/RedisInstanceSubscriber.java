package com.github.wf.server.ws;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis Pub/Sub channel and forwards state changes
 * to local WebSocket clients via InstanceStateDataService + InstanceWebSocketHandler.
 */
@Component
@Profile("redis")
public class RedisInstanceSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisInstanceSubscriber.class);
    private static final String CHANNEL_PATTERN = "instance:state:*";

    private final RedisMessageListenerContainer container;
    private final InstanceWebSocketHandler wsHandler;
    private final InstanceStateDataService dataService;

    public RedisInstanceSubscriber(RedisMessageListenerContainer container,
                                   InstanceWebSocketHandler wsHandler,
                                   InstanceStateDataService dataService) {
        this.container = container;
        this.wsHandler = wsHandler;
        this.dataService = dataService;
    }

    @PostConstruct
    void subscribe() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL_PATTERN));
        // Wire connect callback for snapshot
        wsHandler.setOnConnect(session -> {
            String instanceId = getInstanceId(session);
            if (instanceId != null) {
                try {
                    String json = dataService.buildSnapshot(instanceId);
                    if (json != null) wsHandler.sendToSession(session, json);
                } catch (Exception e) { log.warn("Snapshot error: {}", e.getMessage()); }
            }
        });
        log.info("Redis subscriber registered on pattern '{}'", CHANNEL_PATTERN);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String instanceId = new String(message.getBody());
        if (!wsHandler.hasSubscribers(instanceId)) return;
        try {
            String json = dataService.buildUpdate(instanceId);
            if (json != null) wsHandler.broadcast(instanceId, json);
        } catch (Exception e) {
            log.warn("Redis message handle error: instance={} msg={}", instanceId, e.getMessage());
        }
    }

    private static String getInstanceId(org.springframework.web.socket.WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }
}

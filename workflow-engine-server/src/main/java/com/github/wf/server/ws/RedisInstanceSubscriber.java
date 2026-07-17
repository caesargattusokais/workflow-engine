package com.github.wf.server.ws;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis Pub/Sub channel and forwards state changes
 * to local WebSocket clients via InstanceWebSocketHandler.
 */
@Component
@Profile("redis")
public class RedisInstanceSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisInstanceSubscriber.class);
    private static final String CHANNEL_PATTERN = "instance:state:*";

    private final RedisMessageListenerContainer container;
    private final InstanceWebSocketHandler wsHandler;

    public RedisInstanceSubscriber(RedisMessageListenerContainer container,
                                   InstanceWebSocketHandler wsHandler) {
        this.container = container;
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    void subscribe() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL_PATTERN));
        log.info("Redis subscriber registered on pattern '{}'", CHANNEL_PATTERN);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String instanceId = new String(message.getBody());
        log.debug("Redis message received: instance={}", instanceId);
        wsHandler.pushUpdate(instanceId);
    }
}

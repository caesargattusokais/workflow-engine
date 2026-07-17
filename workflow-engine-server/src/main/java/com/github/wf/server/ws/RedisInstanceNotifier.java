package com.github.wf.server.ws;

import com.github.wf.engine.InstanceStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub mode: publishes instance state changes to a Redis channel
 * so that ALL cluster nodes can push to their local WebSocket clients.
 */
@Component
@Profile("redis")
public class RedisInstanceNotifier implements InstanceStateListener {

    private static final Logger log = LoggerFactory.getLogger(RedisInstanceNotifier.class);
    private static final String CHANNEL_PREFIX = "instance:state:";

    private final StringRedisTemplate redisTemplate;

    public RedisInstanceNotifier(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onStateChanged(String instanceId) {
        log.info("Redis publish: instance={}", instanceId);
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + instanceId, instanceId);
        } catch (Exception e) {
            log.warn("Redis publish failed: instance={} msg={}", instanceId, e.getMessage());
        }
    }
}

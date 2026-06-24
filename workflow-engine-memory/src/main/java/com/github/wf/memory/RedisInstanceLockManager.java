package com.github.wf.memory;

import com.github.wf.engine.InstanceLockManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Distributed per-instance lock backed by Redis SET NX PX + Lua release. */
public class RedisInstanceLockManager implements InstanceLockManager {

    private final StringRedisTemplate redis;
    private static final String PREFIX = "wf:lock:instance:";
    private static final long LOCK_MS = 30_000;
    private static final long RETRY_MS = 50;
    private static final int MAX_RETRIES = 100;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end", Long.class);
    }

    public RedisInstanceLockManager(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void lock(String instanceId) {
        String key = PREFIX + instanceId;
        String token = UUID.randomUUID().toString();
        // Store token in a ThreadLocal-like way — we just use a simple map keyed by instanceId
        // But since lock()/unlock() are always paired in trigger(), we can use a thread-local approach
        // Simpler: store token via SET and retry
        for (int i = 0; i < MAX_RETRIES; i++) {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(LOCK_MS));
            if (Boolean.TRUE.equals(ok)) {
                LockToken.set(instanceId, token);
                return;
            }
            try { Thread.sleep(RETRY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new RuntimeException("Failed to acquire lock for instance: " + instanceId);
    }

    @Override
    public void unlock(String instanceId) {
        String key = PREFIX + instanceId;
        String token = LockToken.remove(instanceId);
        if (token != null) {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        }
    }

    /** Thread-local storage for lock ownership tokens. */
    private static class LockToken {
        private static final ThreadLocal<java.util.Map<String, String>> holder = ThreadLocal.withInitial(java.util.HashMap::new);
        static void set(String id, String token) { holder.get().put(id, token); }
        static String remove(String id) { return holder.get().remove(id); }
    }
}

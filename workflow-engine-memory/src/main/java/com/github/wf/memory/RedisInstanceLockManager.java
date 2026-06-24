package com.github.wf.memory;

import com.github.wf.engine.InstanceLockManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Distributed read/write lock backed by Redis SET NX PX + Lua release.
 *  Reads and writes both use exclusive SETNX — correct and safe. */
public class RedisInstanceLockManager implements InstanceLockManager {

    private final StringRedisTemplate redis;
    private static final String KEY_PREFIX = "wf:lock:";
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

    // ── write lock (exclusive) ──

    @Override
    public void writeLock(String key) {
        acquire(KEY_PREFIX + "w:" + key);
    }

    @Override
    public void writeUnlock(String key) {
        release(KEY_PREFIX + "w:" + key);
    }

    // ── read lock (exclusive across JVMs, safe) ──

    @Override
    public void readLock(String key) {
        acquire(KEY_PREFIX + "r:" + key);
    }

    @Override
    public void readUnlock(String key) {
        release(KEY_PREFIX + "r:" + key);
    }

    // ── internal ──

    private void acquire(String key) {
        String token = UUID.randomUUID().toString();
        for (int i = 0; i < MAX_RETRIES; i++) {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(LOCK_MS));
            if (Boolean.TRUE.equals(ok)) {
                LockToken.set(key, token);
                return;
            }
            try { Thread.sleep(RETRY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new RuntimeException("Failed to acquire lock: " + key);
    }

    private void release(String key) {
        String token = LockToken.remove(key);
        if (token != null) {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        }
    }

    /** Thread-local storage for lock ownership tokens. */
    private static class LockToken {
        private static final ThreadLocal<java.util.Map<String, String>> holder = ThreadLocal.withInitial(java.util.HashMap::new);
        static void set(String key, String token) { holder.get().put(key, token); }
        static String remove(String key) { return holder.get().remove(key); }
    }
}

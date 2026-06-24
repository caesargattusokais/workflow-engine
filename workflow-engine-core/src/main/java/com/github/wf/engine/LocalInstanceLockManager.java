package com.github.wf.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Default in-process lock manager — same behaviour as the original WorkflowEngine.instanceLocks map. */
public class LocalInstanceLockManager implements InstanceLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public void lock(String instanceId) {
        locks.computeIfAbsent(instanceId, k -> new ReentrantLock()).lock();
    }

    @Override
    public void unlock(String instanceId) {
        ReentrantLock lock = locks.get(instanceId);
        if (lock != null) lock.unlock();
    }
}

package com.github.wf.engine;

/** Distributed lock for per-instance mutual exclusion during trigger loops. */
public interface InstanceLockManager {
    void lock(String instanceId);
    void unlock(String instanceId);
}

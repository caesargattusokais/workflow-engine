package com.github.wf.engine;

/** Distributed read/write lock — used for per-instance and per-task mutual exclusion. */
public interface InstanceLockManager {
    /** Exclusive lock (write). */
    void writeLock(String key);
    void writeUnlock(String key);

    /** Shared lock (read). */
    void readLock(String key);
    void readUnlock(String key);

    /** Convenience — delegates to writeLock. */
    default void lock(String key) { writeLock(key); }
    default void unlock(String key) { writeUnlock(key); }
}

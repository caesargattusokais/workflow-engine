package com.github.wf.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Local in-process read/write lock manager backed by ConcurrentHashMap. */
public class LocalInstanceLockManager implements InstanceLockManager {

    private final ConcurrentHashMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private ReadWriteLock rw(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    @Override
    public void writeLock(String key) { rw(key).writeLock().lock(); }

    @Override
    public void writeUnlock(String key) {
        ReadWriteLock l = locks.get(key);
        if (l != null) l.writeLock().unlock();
    }

    @Override
    public void readLock(String key) { rw(key).readLock().lock(); }

    @Override
    public void readUnlock(String key) {
        ReadWriteLock l = locks.get(key);
        if (l != null) l.readLock().unlock();
    }
}

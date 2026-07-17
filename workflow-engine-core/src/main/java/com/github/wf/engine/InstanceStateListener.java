package com.github.wf.engine;

/**
 * Callback invoked when a process instance's state changes during trigger().
 * Implementations can push real-time updates via WebSocket, MQ, etc.
 */
@FunctionalInterface
public interface InstanceStateListener {
    void onStateChanged(String instanceId);
}

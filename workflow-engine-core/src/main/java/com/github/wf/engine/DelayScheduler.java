package com.github.wf.engine;

import java.util.function.Consumer;

/**
 * Pluggable delayed-trigger scheduler.
 * <p>
 * Default implementation ({@link LocalDelayScheduler}) uses an in-memory
 * {@code DelayQueue} with a daemon thread. MQ-based implementations
 * (e.g. RocketMQ) can replace it by sending delayed messages to a broker
 * and consuming them in a listener.
 */
public interface DelayScheduler {

    /** Schedule a trigger for the given instance after {@code delayMs} milliseconds. */
    void schedule(String instanceId, long delayMs);

    /**
     * Start the scheduler.
     * @param triggerFn callback to invoke when a delayed trigger fires (typically {@code engine::trigger})
     */
    default void start(Consumer<String> triggerFn) {}

    /** Stop the scheduler. Called during engine shutdown. */
    default void stop() {}
}

package com.github.wf.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * In-memory delay scheduler backed by a {@link DelayQueue} and a daemon thread.
 * This is the default implementation used when no MQ is configured.
 */
public class LocalDelayScheduler implements DelayScheduler {

    private static final Log log = LogFactory.getLog(LocalDelayScheduler.class);

    private final DelayQueue<DelayedTrigger> delayQueue = new DelayQueue<>();
    private volatile Consumer<String> triggerFn;
    private volatile Thread daemon;

    @Override
    public synchronized void start(Consumer<String> triggerFn) {
        if (daemon != null && daemon.isAlive()) return;
        this.triggerFn = triggerFn;
        daemon = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DelayedTrigger dt = delayQueue.take();
                    log.warn("Delayed trigger: " + dt.instanceId);
                    triggerFn.accept(dt.instanceId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "wf-delay-daemon");
        daemon.setDaemon(true);
        daemon.start();
        log.info("LocalDelayScheduler daemon started");
    }

    @Override
    public synchronized void stop() {
        if (daemon != null) {
            daemon.interrupt();
            daemon = null;
            log.info("LocalDelayScheduler daemon stopped");
        }
    }

    @Override
    public void schedule(String instanceId, long delayMs) {
        delayQueue.put(new DelayedTrigger(instanceId, delayMs));
    }

    // ── Inner class ────────────────────

    static class DelayedTrigger implements Delayed {
        final String instanceId;
        final long deadline; // System.nanoTime() — monotonic

        DelayedTrigger(String instanceId, long delayMs) {
            this.instanceId = instanceId;
            this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(deadline, ((DelayedTrigger) o).deadline);
        }
    }
}

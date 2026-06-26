package com.github.wf.memory;

import com.github.wf.engine.DelayScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Delay scheduler backed by RocketMQ delayed messages.
 * <p>
 * Uses RocketMQ's 18 built-in delay levels (1s to 2h).
 * Delays exceeding the max level are capped at 2h.
 * <p>
 * Consumer lifecycle is managed via {@link #start()} / {@link #stop()}.
 */
public class RocketMQDelayScheduler implements DelayScheduler {

    private static final Log log = LogFactory.getLog(RocketMQDelayScheduler.class);

    static final String TOPIC = "WF_DELAY_TRIGGER";
    private static final String CONSUMER_GROUP = "wf-delay-consumer";

    /** RocketMQ built-in delay levels: index i = level i+1's approximate delay in ms */
    private static final long[] LEVEL_DELAYS = {
        1_000L,     // level  1: 1s
        5_000L,     // level  2: 5s
        10_000L,    // level  3: 10s
        30_000L,    // level  4: 30s
        60_000L,    // level  5: 1m
        120_000L,   // level  6: 2m
        180_000L,   // level  7: 3m
        240_000L,   // level  8: 4m
        300_000L,   // level  9: 5m
        360_000L,   // level 10: 6m
        420_000L,   // level 11: 7m
        480_000L,   // level 12: 8m
        540_000L,   // level 13: 9m
        600_000L,   // level 14: 10m
        1_200_000L, // level 15: 20m
        1_800_000L, // level 16: 30m
        3_600_000L, // level 17: 1h
        7_200_000L  // level 18: 2h
    };

    private final org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;
    private volatile java.util.function.Consumer<String> triggerFn;
    private volatile org.apache.rocketmq.client.consumer.DefaultLitePullConsumer consumer;

    public RocketMQDelayScheduler(
            org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void schedule(String instanceId, long delayMs) {
        int level = delayMsToLevel(delayMs);
        log.info("RocketMQ delayed message: instance=" + instanceId + " delay=" + delayMs + "ms level=" + level);
        org.springframework.messaging.Message<String> msg =
            org.springframework.messaging.support.MessageBuilder.withPayload(instanceId).build();
        rocketMQTemplate.syncSend(TOPIC, msg, 3000, level);
    }

    @Override
    public synchronized void start(java.util.function.Consumer<String> triggerFn) {
        if (consumer != null) return;
        this.triggerFn = triggerFn;
        try {
            consumer = new org.apache.rocketmq.client.consumer.DefaultLitePullConsumer(CONSUMER_GROUP);
            consumer.setNamesrvAddr(rocketMQTemplate.getProducer().getNamesrvAddr());
            consumer.subscribe(TOPIC, "*");
            consumer.setPullBatchSize(1);
            consumer.start();
            log.info("RocketMQDelayScheduler consumer started on topic=" + TOPIC);

            // Background polling thread
            Thread poller = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted() && consumer != null) {
                    try {
                        var msgs = consumer.poll(5000);
                        if (msgs != null) {
                            for (var msg : msgs) {
                                String instanceId = new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                                log.info("Received delayed trigger: instance=" + instanceId);
                                try {
                                    this.triggerFn.accept(instanceId);
                                } catch (Exception e) {
                                    log.error("Error triggering instance " + instanceId, e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            log.error("Poll error", e);
                        }
                    }
                }
            }, "wf-rocketmq-consumer");
            poller.setDaemon(true);
            poller.start();
        } catch (Exception e) {
            log.error("Failed to start RocketMQ consumer", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (consumer != null) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down RocketMQ consumer", e);
            }
            consumer = null;
            log.info("RocketMQDelayScheduler consumer stopped");
        }
    }

    /** Map an arbitrary delay to the nearest RocketMQ delay level (>= requested). */
    private int delayMsToLevel(long delayMs) {
        for (int i = 0; i < LEVEL_DELAYS.length; i++) {
            if (delayMs <= LEVEL_DELAYS[i]) return i + 1;
        }
        return 18; // cap at max level (2h)
    }
}

package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.TimerNode;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TimerRunner implements NodeRunner {

    private final BiConsumer<String, Long> retryScheduler;

    public TimerRunner() { this.retryScheduler = null; }
    public TimerRunner(BiConsumer<String, Long> retryScheduler) {
        this.retryScheduler = retryScheduler;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        TimerNode timer = (TimerNode) node;
        Execution exec = context.getExecution();

        // Calculate delay in milliseconds
        long delayMs = computeDelay(timer);
        if (delayMs <= 0) {
            // No delay — move to next node immediately
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (!outgoing.isEmpty()) {
                exec.setCurrentNodeId(outgoing.get(0).getTo());
            }
            return true;
        }

        // Schedule wake-up via existing retry infrastructure
        if (retryScheduler != null) {
            retryScheduler.accept(exec.getInstanceId(), delayMs);
        }
        exec.setStatus(ExecutionStatus.WAITING);
        exec.setRetryState("TIMER_PENDING");
        return true;
    }

    private long computeDelay(TimerNode timer) {
        long delay = Long.MAX_VALUE;

        if (timer.getDeadline() != null && !timer.getDeadline().isBlank()) {
            try {
                long ms = Instant.parse(timer.getDeadline()).toEpochMilli() - System.currentTimeMillis();
                delay = Math.min(delay, Math.max(0, ms));
            } catch (Exception ignored) {}
        }
        if (timer.getDuration() != null && !timer.getDuration().isBlank()) {
            try {
                delay = Math.min(delay, Duration.parse(timer.getDuration()).toMillis());
            } catch (Exception ignored) {}
        }
        return delay == Long.MAX_VALUE ? 0 : delay;
    }
}

package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.TimerNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TimerRunner implements NodeRunner {

    private static final Log log = LogFactory.getLog(TimerRunner.class);
    private final BiConsumer<String, Long> scheduler;

    public TimerRunner() { this.scheduler = null; }
    public TimerRunner(BiConsumer<String, Long> scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {

        log.info("timer node " +node.getId() +" fired");
        TimerNode timer = (TimerNode) node;
        Execution exec = context.getExecution();
        com.github.wf.spi.InstanceRepository repo = context.getInstanceRepository();
        ProcessInstance instance = repo.findById(context.getInstanceId());

        String firedKey = com.github.wf.engine.InternalVars.timerFiredKey(node.getId());
        // If re-entering after daemon wake-up, advance immediately
        if (Boolean.TRUE.equals(instance.getVariable(firedKey))) {
            instance.removeVariable(firedKey);
            repo.update(instance);
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (outgoing.isEmpty()) return suspendNoRoute(node, exec, instance, repo);
            exec.setCurrentNodeId(outgoing.get(0).getTo());
            exec.setNextRetryAt(0);
            return true;
        }

        long delayMs = computeDelay(timer);
        if (delayMs <= 0) {
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (outgoing.isEmpty()) return suspendNoRoute(node, exec, instance, repo);
            exec.setCurrentNodeId(outgoing.get(0).getTo());
            exec.setNextRetryAt(0);
            log.info("next node " + exec.getCurrentNodeId() + " in");
            return true;
        }

        // First entry: mark and schedule. The due time MUST be persisted —
        // recover() and trigger() use it to avoid firing an unexpired timer.
        instance.setVariable(firedKey, true);
        repo.update(instance);
        exec.setNextRetryAt(System.currentTimeMillis() + delayMs);
        repo.updateExecution(exec);
        if (scheduler != null) {
            scheduler.accept(exec.getInstanceId(), delayMs);
        }
        exec.setStatus(ExecutionStatus.WAITING);
        exec.setRetryState(com.github.wf.engine.RetryState.TIMER_PENDING.name());
        return true;
    }

    private boolean suspendNoRoute(Node node, Execution exec, ProcessInstance instance,
                                   com.github.wf.spi.InstanceRepository repo) {
        String reason = "Timer node has no outgoing transition: " + node.getId();
        log.error(reason);
        exec.setStatus(ExecutionStatus.WAITING);
        exec.setRetryState(com.github.wf.engine.RetryState.SUSPENDED.name());
        exec.setNextRetryAt(0);
        repo.updateExecution(exec);
        instance.setVariable(com.github.wf.engine.InternalVars.suspendReasonKey(node.getId()), reason);
        repo.update(instance);
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

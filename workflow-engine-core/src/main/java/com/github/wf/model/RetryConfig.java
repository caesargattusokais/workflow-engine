package com.github.wf.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RetryConfig {
    private final int maxAttempts;
    private final long delayMs;
    private final double backoffMultiplier;
    private final List<Condition> retryOn;

    public RetryConfig(int maxAttempts, long delayMs, double backoffMultiplier, List<Condition> retryOn) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.delayMs = Math.max(0, delayMs);
        this.backoffMultiplier = Math.max(1.0, backoffMultiplier);
        this.retryOn = retryOn != null ? Collections.unmodifiableList(retryOn) : Collections.emptyList();
    }

    public int getMaxAttempts() { return maxAttempts; }
    public long getDelayMs() { return delayMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public List<Condition> getRetryOn() { return retryOn; }

    /** Calculate delay for attempt N (0-based): delayMs * backoffMultiplier^attempt */
    public long calculateDelay(int attempt) {
        return (long) (delayMs * Math.pow(backoffMultiplier, attempt));
    }
}

package com.github.wf.engine;

/**
 * Pending-state markers stored in {@link Execution#getRetryState()}.
 *
 * <p>The field itself remains a {@code String} because the value is persisted
 * verbatim (JDBC column and Redis JSON); use {@link #name()} at call sites.
 * The enum exists so the set of legal values has a single definition.</p>
 */
public enum RetryState {

    /** Execution failed permanently for now — instance suspended, waiting for resume. */
    SUSPENDED,

    /** Boundary/timer fired — execution waiting for a daemon wake-up. */
    TIMER_PENDING,

    /** Service task failed — retry scheduled via the delay queue. */
    RETRY_PENDING
}

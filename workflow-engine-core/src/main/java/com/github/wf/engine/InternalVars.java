package com.github.wf.engine;

/**
 * Names of internal engine variables stored in {@code ProcessInstance.variables}.
 *
 * <p>These keys are engine-owned: they never come from user-supplied process
 * variables, and the engine cleans them up on resume. Keep the suffix constants
 * in sync with persisted data — old instances already store the exact strings
 * built from them.</p>
 */
public final class InternalVars {

    /** Suffix of the per-node key holding the suspension reason message. */
    public static final String SUSPEND_REASON_SUFFIX = "_suspendReason";
    /** Suffix of the per-node key holding the suspension exception class name. */
    public static final String SUSPEND_EXCEPTION_SUFFIX = "_suspendException";
    /** Suffix of the per-node key marking a fired timer node. */
    public static final String TIMER_FIRED_SUFFIX = "_timerFired";
    /** Suffix of the per-node key marking a fired boundary timer on a user task. */
    public static final String BOUNDARY_TIMER_FIRED_SUFFIX = "_boundaryTimerFired";

    private InternalVars() {}

    public static String suspendReasonKey(String nodeId) {
        return nodeId + SUSPEND_REASON_SUFFIX;
    }

    public static String suspendExceptionKey(String nodeId) {
        return nodeId + SUSPEND_EXCEPTION_SUFFIX;
    }

    public static String timerFiredKey(String nodeId) {
        return nodeId + TIMER_FIRED_SUFFIX;
    }

    public static String boundaryTimerFiredKey(String nodeId) {
        return nodeId + BOUNDARY_TIMER_FIRED_SUFFIX;
    }

    /** Returns true if the given variable key is engine-internal (any of the suffixes). */
    public static boolean isInternalKey(String key) {
        return key != null && (key.endsWith(SUSPEND_REASON_SUFFIX)
                || key.endsWith(SUSPEND_EXCEPTION_SUFFIX)
                || key.endsWith(TIMER_FIRED_SUFFIX)
                || key.endsWith(BOUNDARY_TIMER_FIRED_SUFFIX));
    }
}

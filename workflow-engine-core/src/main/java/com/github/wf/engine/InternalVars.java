package com.github.wf.engine;

import java.util.Map;

/**
 * Names of internal engine variables stored in {@code ProcessInstance.variables}.
 *
 * <p>These keys are engine-owned: they never come from user-supplied process
 * variables, and the engine cleans them up on resume. Keep the suffix constants
 * in sync with persisted data — old instances already store the exact strings
 * built from them.</p>
 */
public final class InternalVars {

    /** Instance owner key — engine-controlled, never settable via completeTask variables. */
    public static final String OWNER_KEY = "_userId";

    /**
     * Flag set to {@code true} the first time any task on the instance is
     * rejected (initialized to {@code false} at start). Lets process models
     * route on "has something been rejected" — e.g. an inclusive join sending
     * partially-rejected instances to a dedicated reject end instead of
     * completing them as if everything passed.
     */
    public static final String HAS_REJECT = "_hasReject";

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
        return key != null && (OWNER_KEY.equals(key)
                || HAS_REJECT.equals(key)
                || key.endsWith(SUSPEND_REASON_SUFFIX)
                || key.endsWith(SUSPEND_EXCEPTION_SUFFIX)
                || key.endsWith(TIMER_FIRED_SUFFIX)
                || key.endsWith(BOUNDARY_TIMER_FIRED_SUFFIX));
    }

    /** Strip engine-owned keys from a user-supplied variable map (defensive copy). */
    public static Map<String, Object> stripInternal(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) return Map.of();
        Map<String, Object> clean = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            if (!isInternalKey(e.getKey())) clean.put(e.getKey(), e.getValue());
        }
        return clean;
    }
}

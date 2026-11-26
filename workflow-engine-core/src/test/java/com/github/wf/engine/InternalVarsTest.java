package com.github.wf.engine;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InternalVarsTest {

    @Test
    void ownerKeyIsInternal() {
        assertThat(InternalVars.isInternalKey("_userId")).isTrue();
        assertThat(InternalVars.isInternalKey("amount")).isFalse();
    }

    @Test
    void timerAndSuspendKeysAreInternal() {
        assertThat(InternalVars.isInternalKey("taskA_boundaryTimerFired")).isTrue();
        assertThat(InternalVars.isInternalKey("wait_timerFired")).isTrue();
        assertThat(InternalVars.isInternalKey("node_suspendReason")).isTrue();
        assertThat(InternalVars.isInternalKey("node_suspendException")).isTrue();
    }

    @Test
    void stripInternalRemovesEngineKeysOnly() {
        Map<String, Object> clean = InternalVars.stripInternal(Map.of(
                "amount", 1000,
                "_userId", "victim",
                "approve_timerFired", true,
                "approve_suspendReason", "forged"));
        assertThat(clean).containsEntry("amount", 1000).hasSize(1);
        assertThat(clean).doesNotContainKeys("_userId", "approve_timerFired", "approve_suspendReason");
    }

    @Test
    void stripInternalKeepsOriginalMapUnchanged() {
        Map<String, Object> original = new java.util.HashMap<>(Map.of("_userId", "admin", "x", 1));
        InternalVars.stripInternal(original);
        assertThat(original).containsEntry("_userId", "admin");
    }
}

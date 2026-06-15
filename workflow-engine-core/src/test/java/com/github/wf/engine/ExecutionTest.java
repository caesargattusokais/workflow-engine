package com.github.wf.engine;

import com.github.wf.model.ExecutionStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTest {

    @Test
    void rootExecutionHasNoParent() {
        Execution exec = new Execution("inst-1", "start");
        assertThat(exec.isChild()).isFalse();
        assertThat(exec.getParentExecutionId()).isNull();
        assertThat(exec.isActive()).isTrue();
    }

    @Test
    void childExecutionHasParent() {
        Execution child = new Execution("child-1", "inst-1", "task-a", "parent-1");
        assertThat(child.isChild()).isTrue();
        assertThat(child.getParentExecutionId()).isEqualTo("parent-1");
    }

    @Test
    void statusTransitions() {
        Execution exec = new Execution("inst-1", "start");
        assertThat(exec.isActive()).isTrue();
        exec.setStatus(ExecutionStatus.WAITING);
        assertThat(exec.isWaiting()).isTrue();
        exec.setStatus(ExecutionStatus.COMPLETED);
        assertThat(exec.isCompleted()).isTrue();
    }
}

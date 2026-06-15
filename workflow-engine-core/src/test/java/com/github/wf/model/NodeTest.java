package com.github.wf.model;

import com.github.wf.model.node.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeTest {

    @Test
    void startEventHasCorrectType() {
        StartEvent node = new StartEvent("start");
        assertThat(node.getType()).isEqualTo(NodeType.START_EVENT);
        assertThat(node.getId()).isEqualTo("start");
        assertThat(node.getName()).isEqualTo("start");
    }

    @Test
    void userTaskStoresAssigneeAndGroups() {
        UserTask task = new UserTask("t1", "审批", "${user}",
                List.of("manager", "hr"), null, null);
        assertThat(task.getAssignee()).isEqualTo("${user}");
        assertThat(task.getCandidateGroups()).containsExactly("manager", "hr");
        assertThat(task.getDynamicRouter()).isNull();
    }

    @Test
    void serviceTaskStoresHandlerClass() {
        ServiceTask task = new ServiceTask("svc", "发通知",
                "com.myapp.SendNotification", null);
        assertThat(task.getHandlerClass()).isEqualTo("com.myapp.SendNotification");
    }

    @Test
    void exclusiveGatewayHasCorrectType() {
        ExclusiveGateway gw = new ExclusiveGateway("gw1");
        assertThat(gw.getType()).isEqualTo(NodeType.EXCLUSIVE_GATEWAY);
    }

    @Test
    void parallelGatewayHasCorrectType() {
        ParallelGateway gw = new ParallelGateway("fork");
        assertThat(gw.getType()).isEqualTo(NodeType.PARALLEL_GATEWAY);
    }

    @Test
    void inclusiveGatewayHasCorrectType() {
        InclusiveGateway gw = new InclusiveGateway("incl");
        assertThat(gw.getType()).isEqualTo(NodeType.INCLUSIVE_GATEWAY);
    }

    @Test
    void nodeListenersDefaultToEmptyList() {
        StartEvent node = new StartEvent("s");
        assertThat(node.getListeners()).isEmpty();
    }

    @Test
    void nodeEqualityById() {
        StartEvent a = new StartEvent("x");
        EndEvent b = new EndEvent("x");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void nullIdThrows() {
        assertThatThrownBy(() -> new StartEvent(null))
                .isInstanceOf(NullPointerException.class);
    }
}

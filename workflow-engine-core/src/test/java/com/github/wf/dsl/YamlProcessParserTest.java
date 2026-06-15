package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.io.Reader;
import static org.assertj.core.api.Assertions.assertThat;

class YamlProcessParserTest {

    private final YamlProcessParser parser = new YamlProcessParser();

    @Test
    void parsesLeaveApprovalYaml() {
        Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/leave-approval.yaml"));
        ProcessDefinition def = parser.parse(reader);

        assertThat(def.getId()).isEqualTo("leave-approval");
        assertThat(def.getName()).isEqualTo("请假审批");
        assertThat(def.getVersion()).isEqualTo(1);
        assertThat(def.getNodes()).hasSize(6);
        assertThat(def.getStartNode().getId()).isEqualTo("start");

        Node apply = def.getNode("apply");
        assertThat(apply).isInstanceOf(UserTask.class);
        assertThat(((UserTask) apply).getAssignee()).isEqualTo("${applicant}");

        Node gw = def.getNode("gateway");
        assertThat(gw).isInstanceOf(ExclusiveGateway.class);
        assertThat(def.getOutgoingTransitions("gateway")).hasSize(2);
        assertThat(def.getOutgoingTransitions("gateway").get(0).isConditional()).isTrue();
        assertThat(def.getOutgoingTransitions("gateway").get(1).isDefault()).isTrue();
    }

    @Test
    void parsesListeners() {
        Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/leave-approval.yaml"));
        ProcessDefinition def = parser.parse(reader);
        Node managerNode = def.getNode("manager-approve");
        assertThat(managerNode.getListeners()).contains("com.myapp.NotifyListener");
    }
}

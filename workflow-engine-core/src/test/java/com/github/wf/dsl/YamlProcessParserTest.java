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

    // ── class→className field alias mapping tests ──

    @Test
    void gatewayCondition_classFieldMappedToClassName() {
        ProcessDefinition def = parser.parse("""
                id: class-field-test
                name: Java条件测试
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: gw
                    type: exclusiveGateway
                    conditions:
                      - class: "com.test.MyCondition"
                        to: branch-a
                      - default: true
                        to: branch-b
                  - id: branch-a
                    type: endEvent
                  - id: branch-b
                    type: endEvent
                transitions:
                  - from: start
                    to: gw
                """);

        Transition classTransition = def.getOutgoingTransitions("gw").stream()
                .filter(Transition::isConditional)
                .findFirst()
                .orElseThrow();

        Condition cond = classTransition.getCondition();
        assertThat(cond).isNotNull();
        assertThat(cond.getType()).isEqualTo(ConditionType.JAVA_CLASS);
        assertThat(cond.getClassName()).isEqualTo("com.test.MyCondition");
    }

    @Test
    void gatewayCondition_exprFieldStillWorks() {
        ProcessDefinition def = parser.parse("""
                id: expr-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: gw
                    type: exclusiveGateway
                    conditions:
                      - expr: "amount > 100"
                        to: high
                      - default: true
                        to: low
                  - id: high
                    type: endEvent
                  - id: low
                    type: endEvent
                transitions:
                  - from: start
                    to: gw
                """);

        Transition exprTransition = def.getOutgoingTransitions("gw").stream()
                .filter(Transition::isConditional)
                .findFirst()
                .orElseThrow();

        Condition cond = exprTransition.getCondition();
        assertThat(cond).isNotNull();
        assertThat(cond.getType()).isEqualTo(ConditionType.EXPRESSION);
        assertThat(cond.getExpr()).isEqualTo("amount > 100");
    }

    @Test
    void serviceTaskResultRouting_classFieldMappedToClassName() {
        ProcessDefinition def = parser.parse("""
                id: route-class-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    handlerClass: "com.test.Handler"
                    resultRouting:
                      - class: "com.test.SuccessCondition"
                        to: success-end
                      - default: true
                        to: fail-end
                  - id: success-end
                    type: endEvent
                  - id: fail-end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                """);

        Node node = def.getNode("call");
        assertThat(node).isInstanceOf(ServiceTask.class);
        ServiceTask st = (ServiceTask) node;

        assertThat(st.getResultRouting()).hasSize(2);
        assertThat(st.getResultRouting().get(0).getCondition()).isNotNull();
        assertThat(st.getResultRouting().get(0).getCondition().getType()).isEqualTo(ConditionType.JAVA_CLASS);
        assertThat(st.getResultRouting().get(0).getCondition().getClassName()).isEqualTo("com.test.SuccessCondition");
    }

    @Test
    void serviceTaskExceptionRouting_classFieldMappedToClassName() {
        ProcessDefinition def = parser.parse("""
                id: exception-class-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    handlerClass: "com.test.Handler"
                    exceptionRouting:
                      - class: "com.test.RetryableException"
                        to: retry-end
                      - default: true
                        to: fail-end
                  - id: retry-end
                    type: endEvent
                  - id: fail-end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                """);

        ServiceTask st = (ServiceTask) def.getNode("call");

        assertThat(st.getExceptionRouting()).hasSize(2);
        assertThat(st.getExceptionRouting().get(0).getCondition()).isNotNull();
        assertThat(st.getExceptionRouting().get(0).getCondition().getType()).isEqualTo(ConditionType.JAVA_CLASS);
        assertThat(st.getExceptionRouting().get(0).getCondition().getClassName())
                .isEqualTo("com.test.RetryableException");
    }
}

package com.github.wf.server.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiControllerTest {

    private static final String VALID_YAML = """
            id: leave-flow
            name: '请假审批'
            version: 1
            nodes:
              - id: start
                type: startEvent
                name: 开始
              - id: apply
                type: userTask
                name: 提交申请
                assignee: "${applicant}"
              - id: end
                type: endEvent
                name: 结束
            transitions:
              - from: start
                to: apply
              - from: apply
                to: end
            """;

    @Test
    void generateReturnsValidatedYaml() {
        AiController ctrl = new AiController((sys, user) -> "```yaml\n" + VALID_YAML + "\n```");
        Map<String, Object> out = ctrl.generate("u1", Map.of("prompt", "请假审批"));
        assertThat(((String) out.get("yaml")).strip()).isEqualTo(VALID_YAML.strip());
        assertThat(out.get("name")).isEqualTo("请假审批");
        assertThat((Integer) out.get("nodeCount")).isEqualTo(3);
    }

    @Test
    void notConfiguredThrows() {
        AiController ctrl = new AiController(null);
        assertThatThrownBy(() -> ctrl.generate("u1", Map.of("prompt", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI is not configured");
    }

    @Test
    void blankPromptThrows() {
        AiController ctrl = new AiController((sys, user) -> VALID_YAML);
        assertThatThrownBy(() -> ctrl.generate("u1", Map.of("prompt", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidOutputTriggersRepairRetry() {
        String invalidFirst = """
                id: bad-flow
                name: '坏流程'
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: t
                    type: noSuchType
                transitions: []
                """;
        List<String> prompts = new ArrayList<>();
        AiClient recording = (sys, user) -> {
            prompts.add(user);
            return prompts.size() == 1 ? invalidFirst : VALID_YAML;
        };
        AiController ctrl = new AiController(recording);
        Map<String, Object> out = ctrl.generate("u1", Map.of("prompt", "测试"));
        assertThat(((String) out.get("yaml")).strip()).isEqualTo(VALID_YAML.strip());
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(1)).contains("noSuchType");
    }

    @Test
    void unrepairableOutputThrowsWithProblems() {
        String invalid = """
                id: bad
                name: '坏'
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: t
                    type: noSuchType
                transitions: []
                """;
        AiController ctrl = new AiController((sys, user) -> invalid);
        assertThatThrownBy(() -> ctrl.generate("u1", Map.of("prompt", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noSuchType");
    }

    @Test
    void validatorChecksStructure() {
        String danglingEdge = """
                id: x
                name: 'x'
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: ghost
                """;
        assertThat(AiFlowValidator.validate(danglingEdge))
                .anyMatch(e -> e.contains("ghost"));

        String noEnd = """
                id: x
                name: 'x'
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                transitions: []
                """;
        assertThat(AiFlowValidator.validate(noEnd))
                .anyMatch(e -> e.contains("endEvent"));

        String gatewayNoDefault = """
                id: x
                name: 'x'
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: gw
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 1"
                        to: end
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: gw
                  - from: gw
                    to: end
                """;
        assertThat(AiFlowValidator.validate(gatewayNoDefault))
                .anyMatch(e -> e.contains("no default branch"));

        String gatewayWithDefault = gatewayNoDefault.replace(
                "- expr: \"days > 1\"\n        to: end",
                "- expr: \"days > 1\"\n        to: end\n      - default: true\n        to: end");
        assertThat(AiFlowValidator.validate(gatewayWithDefault)).isEmpty();

        assertThat(AiFlowValidator.validate(VALID_YAML)).isEmpty();
    }

    @Test
    void extractYamlHandlesFencesAndTags() {
        assertThat(AiController.extractYaml("```yaml\nabc\n```")).isEqualTo("abc");
        assertThat(AiController.extractYaml("```\nabc\n```")).isEqualTo("abc");
        assertThat(AiController.extractYaml("yaml\nabc")).isEqualTo("abc");
        assertThat(AiController.extractYaml("abc\ndef")).isEqualTo("abc\ndef");
    }
}

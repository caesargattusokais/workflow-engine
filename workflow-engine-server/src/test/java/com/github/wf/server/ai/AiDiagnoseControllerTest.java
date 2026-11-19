package com.github.wf.server.ai;

import com.github.wf.model.ProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiDiagnoseControllerTest {

    @Test
    void parsesStructuredJson() {
        AiDiagnoseController ctrl = new AiDiagnoseController((sys, user) -> "");
        String raw = """
                {"summary":"卡在经理审批","cause":"人工任务待办","suggestions":["催办","或转派"],"severity":"info"}
                """;
        Map<String, Object> out = ctrl.parseDiagnosis(raw);
        assertThat(out.get("summary")).isEqualTo("卡在经理审批");
        assertThat(out.get("cause")).isEqualTo("人工任务待办");
        assertThat((List<String>) out.get("suggestions")).containsExactly("催办", "或转派");
        assertThat(out.get("severity")).isEqualTo("info");
    }

    @Test
    void parsesJsonInsideMarkdownFence() {
        AiDiagnoseController ctrl = new AiDiagnoseController((sys, user) -> "");
        String raw = "```json\n{\"summary\":\"服务失败\",\"severity\":\"error\"}\n```";
        Map<String, Object> out = ctrl.parseDiagnosis(raw);
        assertThat(out.get("summary")).isEqualTo("服务失败");
        assertThat(out.get("severity")).isEqualTo("error");
        assertThat((List<String>) out.get("suggestions")).isEmpty();
    }

    @Test
    void fallsBackToPlainText() {
        AiDiagnoseController ctrl = new AiDiagnoseController((sys, user) -> "");
        Map<String, Object> out = ctrl.parseDiagnosis("AI 返回了一段非 JSON 的说明");
        assertThat(out.get("summary")).isEqualTo("AI 返回了一段非 JSON 的说明");
        assertThat(out.get("severity")).isEqualTo("info");
        assertThat((List<String>) out.get("suggestions")).isEmpty();
    }

    @Test
    void coercesNonStringSuggestionsAndDefaultSeverity() {
        AiDiagnoseController ctrl = new AiDiagnoseController((sys, user) -> "");
        String raw = "{\"summary\":\"x\",\"suggestions\":[1,2],\"severity\":\"\"}";
        Map<String, Object> out = ctrl.parseDiagnosis(raw);
        assertThat((List<String>) out.get("suggestions")).containsExactly("1", "2");
        assertThat(out.get("severity")).isEqualTo("info");
    }

    @Test
    void extractJsonHandlesFenceAndAbsentBraces() {
        assertThat(AiDiagnoseController.extractJson("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(AiDiagnoseController.extractJson("no json here")).isNull();
    }

    @Test
    void buildContextIncludesSuspendReasonAndBusinessVars() {
        AiDiagnoseController ctrl = new AiDiagnoseController((sys, user) -> "");
        ProcessInstance inst = new ProcessInstance("i1", "d1", Map.of(
                "applicant", "zhangsan",
                "extract_suspendReason", "HTTP 500 from billing service",
                "_userId", "u1"));
        inst.setActiveNodeIds(Set.of("extract"));
        String ctx = ctrl.buildContext(inst);
        assertThat(ctx).contains("\"status\"").contains("RUNNING");
        assertThat(ctx).contains("\"activeNodes\"").contains("extract");
        assertThat(ctx).contains("suspendReasons").contains("HTTP 500 from billing service");
        assertThat(ctx).contains("\"variables\"").contains("applicant").contains("zhangsan");
        assertThat(ctx).doesNotContain("_userId");
    }
}

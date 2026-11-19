package com.github.wf.server.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wf.engine.InternalVars;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.ProcessDefinition;
import com.github.wf.model.ProcessInstance;
import com.github.wf.server.web.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI operations co-pilot — explains why an instance is stuck/suspended and
 * suggests recovery steps, by feeding the instance's timeline + suspension
 * reason + business variables to the configured LLM.
 *
 * <p>Read-only: this endpoint never mutates the instance. The human decides
 * whether to resume/terminate. Shares the {@link AiClient} SPI with flow
 * generation, so it honours the same provider configuration and degrades
 * gracefully when AI is not configured.</p>
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@Tag(name = "AI", description = "AI-assisted diagnosis of running/suspended instances")
public class AiDiagnoseController {

    private final AiClient aiClient;
    private final WorkflowEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Tests / embedding without engine: no-op. */
    public AiDiagnoseController(AiClient aiClient) {
        this(aiClient, null);
    }

    @Autowired
    public AiDiagnoseController(AiClient aiClient, WorkflowEngine engine) {
        this.aiClient = aiClient;
        this.engine = engine;
    }

    @PostMapping("/diagnose")
    @Operation(summary = "Explain an instance's current state with AI",
            description = "Returns a structured diagnosis (summary / cause / suggestions / severity) built from the instance timeline and suspendReason. Read-only.")
    public Map<String, Object> diagnose(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        if (aiClient == null) {
            throw new IllegalStateException("AI is not configured — set AI_BASE_URL / AI_API_KEY / AI_MODEL");
        }
        String instanceId = body.get("instanceId");
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId is required");
        }
        ProcessInstance inst = TenantGuard.requireOwnedInstance(engine, userId, instanceId);

        String context = buildContext(inst);
        String raw = aiClient.complete(DIAGNOSE_SYSTEM_PROMPT, context);
        return parseDiagnosis(raw);
    }

    /** Assemble a compact, token-friendly context from engine data. */
    String buildContext(ProcessInstance inst) {
        StringBuilder sb = new StringBuilder();
        sb.append("流程实例诊断上下文（JSON）：\n{\n");
        sb.append("  \"status\": \"").append(inst.getStatus()).append("\",\n");
        sb.append("  \"definitionId\": \"").append(inst.getDefinitionId()).append("\",\n");
        sb.append("  \"definitionVersion\": ").append(inst.getDefinitionVersion()).append(",\n");

        // Active nodes with human-readable names
        Set<String> active = inst.getActiveNodeIds();
        List<String> activeNames = new ArrayList<>();
        for (String nid : active) {
            String label = nid;
            ProcessDefinition def = resolveDefinition(inst);
            if (def != null && def.getNode(nid) != null && def.getNode(nid).getName() != null) {
                label = nid + "(" + def.getNode(nid).getName() + ")";
            }
            activeNames.add(label);
        }
        sb.append("  \"activeNodes\": ").append(str(activeNames)).append(",\n");

        // Suspension reasons — engine-internal keys only, human-readable
        Map<String, String> suspends = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : inst.getVariables().entrySet()) {
            String k = e.getKey();
            if (k.endsWith(InternalVars.SUSPEND_REASON_SUFFIX)) {
                suspends.put(k, String.valueOf(e.getValue()));
            } else if (k.endsWith(InternalVars.SUSPEND_EXCEPTION_SUFFIX)) {
                suspends.put(k, String.valueOf(e.getValue()));
            }
        }
        sb.append("  \"suspendReasons\": ").append(str(suspends)).append(",\n");

        // Business variables (exclude engine internals and bulky payloads)
        Map<String, Object> biz = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : inst.getVariables().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || InternalVars.isInternalKey(k)) continue;
            Object v = e.getValue();
            String vs = v == null ? "null" : String.valueOf(v);
            if (vs.length() > 500) vs = vs.substring(0, 500) + "…";
            biz.put(k, vs);
        }
        sb.append("  \"variables\": ").append(str(biz)).append(",\n");

        // Timeline (last 20 steps)
        List<Map<String, String>> timeline = new ArrayList<>();
        List<HistoricActivity> history = engine != null ? engine.history(inst.getId()) : List.of();
        int from = Math.max(0, history.size() - 20);
        for (int i = from; i < history.size(); i++) {
            HistoricActivity h = history.get(i);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("node", h.getNodeId() + (h.getNodeName() != null ? "(" + h.getNodeName() + ")" : ""));
            m.put("action", String.valueOf(h.getAction()));
            m.put("time", String.valueOf(h.getTimestamp()));
            if (h.getComment() != null) m.put("comment", h.getComment());
            timeline.add(m);
        }
        sb.append("  \"timeline\": ").append(str(timeline)).append("\n}\n");
        return sb.toString();
    }

    private ProcessDefinition resolveDefinition(ProcessInstance inst) {
        if (engine == null) return null;
        return engine.processRepository.findLatestById(inst.getDefinitionId());
    }

    /** Parse the LLM response into a structured diagnosis; fall back to plain text. */
    Map<String, Object> parseDiagnosis(String raw) {
        String text = raw == null ? "" : raw.trim();
        String json = extractJson(text);
        if (json != null) {
            try {
                Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("summary", strVal(parsed.get("summary")));
                out.put("cause", strVal(parsed.get("cause")));
                Object suggestions = parsed.get("suggestions");
                out.put("suggestions", suggestions instanceof List
                        ? ((List<?>) suggestions).stream().map(AiDiagnoseController::strVal).toList()
                        : List.of());
                String sev = strVal(parsed.get("severity"));
                out.put("severity", sev.isBlank() ? "info" : sev);
                return out;
            } catch (Exception ignored) {
                // fall through to plain-text diagnosis
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", text.isBlank() ? "（AI 未返回内容）" : text);
        out.put("cause", "");
        out.put("suggestions", List.of());
        out.put("severity", "info");
        return out;
    }

    /** Grab the first {...} block, stripping markdown fences if present. */
    static String extractJson(String text) {
        String t = text == null ? "" : text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        return start >= 0 && end > start ? t.substring(start, end + 1) : null;
    }

    private static String strVal(Object o) { return o == null ? "" : String.valueOf(o); }

    private String str(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    static final String DIAGNOSE_SYSTEM_PROMPT = """
            你是工作流运维专家。根据给定的流程实例诊断上下文（JSON），用中文解释实例为什么停住/挂起，并给出可操作的恢复建议。
            只输出一个 JSON 对象，不要任何解释文字、不要 markdown 代码围栏，格式：
            {
              "summary": "一句话说明当前状态",
              "cause": "根因分析（结合 suspendReasons 与 timeline）",
              "suggestions": ["建议步骤1", "建议步骤2"],
              "severity": "info|warning|error"
            }
            规则：
            - 若 suspendReasons 有内容，优先据此判断根因（如服务调用失败、AI 调用异常、超时）。
            - 若实例只是 RUNNING 且停在人工任务，说明它在等待审批，属正常，severity 用 info。
            - 建议要具体：是重试、补全表单、检查外部服务、还是人工介入。
            - 不要编造上下文里没有的信息；信息不足时明确说"上下文不足"。
            """;
}

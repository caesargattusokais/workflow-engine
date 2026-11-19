package com.github.wf.server.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI flow generation — natural language to workflow YAML.
 *
 * Pipeline: prompt → LLM (schema-constrained system prompt) → strip code
 * fences → structural validation via the engine's own YAML parser → one
 * automatic repair round (validation errors fed back) → validated YAML.
 * The result lands in the designer canvas as a draft; the human reviews
 * and deploys — AI never deploys directly (human-in-the-loop).
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@Tag(name = "AI", description = "AI-assisted flow generation — natural language to validated workflow YAML")
public class AiController {

    private final AiClient aiClient;
    private final AiGenerationSettings generation;

    /** Tests / embedding without generation settings: built-in defaults. */
    public AiController(AiClient aiClient) {
        this(aiClient, new AiGenerationSettings(null, null, null));
    }

    @Autowired
    public AiController(AiClient aiClient, AiGenerationSettings generation) {
        this.aiClient = aiClient;
        this.generation = generation;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a flow from natural language",
            description = "Returns validated workflow YAML. Requires ai.base-url configuration; 409 when AI is not configured.")
    public Map<String, Object> generate(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        if (aiClient == null) {
            throw new IllegalStateException("AI is not configured — set AI_BASE_URL / AI_API_KEY / AI_MODEL");
        }
        String prompt = body.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }

        String systemPrompt = systemPrompt();
        String raw = aiClient.complete(systemPrompt, prompt);
        String yaml = extractYaml(raw);

        List<String> errors = AiFlowValidator.validate(yaml);
        if (!errors.isEmpty()) {
            // One automatic repair round — feed the problems back to the model
            String retryPrompt = prompt
                    + "\n\n你上一次的输出存在以下问题，请修正后重新输出完整 YAML（只输出 YAML）：\n- "
                    + String.join("\n- ", errors)
                    + "\n\n上一次的输出：\n" + yaml;
            String retryRaw = aiClient.complete(systemPrompt, retryPrompt);
            yaml = extractYaml(retryRaw);
            errors = AiFlowValidator.validate(yaml);
            if (!errors.isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "AI 生成的流程未通过校验");
                err.put("problems", errors);
                throw new IllegalArgumentException(String.join("; ", errors));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yaml", yaml);
        result.put("name", AiFlowValidator.extractName(yaml));
        result.put("nodeCount", AiFlowValidator.nodeCount(yaml));
        return result;
    }

    /** Effective system prompt: page/env override, else the built-in default. */
    private String systemPrompt() {
        String custom = generation.effective().systemPrompt();
        return custom != null && !custom.isBlank() ? custom : DEFAULT_SYSTEM_PROMPT;
    }

    /** Strip markdown code fences the model may wrap the YAML in. */
    static String extractYaml(String text) {
        String t = text == null ? "" : text.trim();
        int fenceStart = t.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = t.indexOf('\n', fenceStart);
            if (contentStart > 0) {
                int fenceEnd = t.indexOf("```", contentStart);
                t = fenceEnd > contentStart ? t.substring(contentStart + 1, fenceEnd) : t.substring(contentStart + 1);
            }
        }
        // Also strip a leading "yaml" language tag if the model wrote it bare
        t = t.trim();
        if (t.startsWith("yaml")) t = t.substring(4).trim();
        return t;
    }

    // ── Prompt ────────────────────────────

    static final String DEFAULT_SYSTEM_PROMPT = """
            你是资深工作流架构师。根据用户的自然语言描述，输出该工作流引擎可用的流程 YAML。只输出 YAML 本身，不要任何解释文字。

            ## 顶层结构
            id: 英文小写中划线（唯一标识）
            name: 中文流程名
            version: 1
            nodes: 节点列表
            transitions: 连线列表

            ## 节点类型（type 只能是以下 10 种）
            startEvent    开始事件（必须恰好 1 个）
            endEvent      结束事件（至少 1 个）
            userTask      人工任务（assignee: 审批人或 "${变量}"；candidateGroups: 候选组列表；boundaryTimer: 超时如 "PT30M"）
            serviceTask   服务任务（httpMode: true 时用 url/method/body 调 HTTP；handlerClass: Java 类；可带 retry: {maxAttempts, delayMs, backoffMultiplier}）
            exclusiveGateway  排他网关（分支写在 transitions 上）
            parallelGateway   并行网关（多条直出边即并行分叉）
            inclusiveGateway  包容网关
            timer             定时器（duration: "PT1H" 或 until: "2026-01-01T09:00:00"）
            callActivity      子流程（calledId + calledVersion，inputMapping/outputMapping: [{from,to}]）
            aiTask            AI 任务（mode: summary/extract/classify；prompt: 提示词可用 ${变量}；options: 分类候选列表；outputVar 可选）
                              产出变量 <节点id>_text / <节点id>_result / <节点id>_category，可由网关 SpEL 条件消费

            ## 连线类型（transitions 每项）
            - from / to 必填，引用已存在的节点 id
            - 默认 direct：直接写 {from, to}
            - timeout：{from, to, type: timeout}（配合 userTask 的 boundaryTimer）
            - result / exception：{from, to, type: result|exception, expr: "..."}（serviceTask 按结果/异常路由）

            ## 网关分支（重要：写在网关节点上，不是 transitions 上）
            exclusiveGateway / inclusiveGateway 的分支用节点级 conditions 表达：
              - id: gateway
                type: exclusiveGateway
                name: 天数判断
                conditions:
                  - expr: "days <= 3"
                    to: manager
                  - default: true
                    to: director
            - expr 是 SpEL，引用用户提到的业务变量（如 days、amount），不要加 ${}
            - 每个 conditions 列表必须包含一项 {default: true, to: ...} 兜底
            - 不要把 conditional/default 分支写到 transitions 里

            ## 硬性规则
            1. 节点 id 用英文小写驼峰，name 用中文
            2. 恰好一个 startEvent，至少一个 endEvent
            3. 所有 from/to 必须指向存在的节点
            4. 排他网关的每个 conditional 分支要有 expr，且必须配一条 default 兜底边
            5. 流程必须能从 start 连通到 end，不要有孤岛节点
            6. 条件里引用的变量名要贴合用户描述的业务字段（如 days、amount），并在描述未提及时选择自然合理的名字

            ## 示例（用户说"请假 3 天内经理审批，超过 3 天总监审批，超时 30 分钟升级"）
            id: leave-approval
            name: '请假审批'
            version: 1
            nodes:
              - id: start
                type: startEvent
                name: 开始
              - id: apply
                type: userTask
                name: 提交请假申请
                assignee: "${applicant}"
              - id: gateway
                type: exclusiveGateway
                name: 天数判断
                conditions:
                  - expr: "days <= 3"
                    to: manager
                  - default: true
                    to: director
              - id: manager
                type: userTask
                name: 经理审批
                assignee: manager
                boundaryTimer: "PT30M"
              - id: director
                type: userTask
                name: 总监审批
                assignee: director
              - id: escalation
                type: userTask
                name: 超时升级
                assignee: director
              - id: end
                type: endEvent
                name: 结束
            transitions:
              - from: start
                to: apply
              - from: apply
                to: gateway
              - from: manager
                to: end
              - from: manager
                to: escalation
                type: timeout
              - from: director
                to: end
              - from: escalation
                to: end
            """;
}

package com.github.wf.engine.runner;

import com.github.wf.engine.*;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.AiTask;
import com.github.wf.model.node.EndEvent;
import com.github.wf.spi.InstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AiTaskRunnerTest {

    private InstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;
    private ProcessDefinition def;
    private AiTaskRunner runner;

    @BeforeEach
    void setUp() {
        exprEval = new SpelExpressionEvaluator();
        final Map<String, ProcessInstance> instances = new HashMap<>();
        final Map<String, Execution> executions = new HashMap<>();
        instanceRepo = new InstanceRepository() {
            public void save(ProcessInstance i) { instances.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instances.get(id); }
            public void update(ProcessInstance i) { instances.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) { return List.of(); }
            public void saveExecution(Execution e) { executions.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executions.get(id); }
            public List<Execution> findActiveExecutions(String i) { return List.of(); }
            public List<Execution> findExecutionsByParentId(String p) { return List.of(); }
            public void updateExecution(Execution e) { executions.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) {}
            public List<HistoricActivity> findHistory(String i) { return List.of(); }
        };
        instanceRepo.save(new ProcessInstance("inst-1", "wf",
                Map.of("document", "张三请假5天", "amount", 100)));
        runner = new AiTaskRunner();
    }

    private ExecutionContext ctxFor(Execution exec, ProcessDefinition definition) {
        return new ExecutionContext(definition, exec, exprEval, instanceRepo);
    }

    private ProcessDefinition linearDef(AiTask task) {
        return new ProcessDefinition("wf", "Test", 1,
                List.of(task, new EndEvent("end")),
                List.of(Transition.direct("ai", "end")));
    }

    @Test
    void summaryWritesNamespacedTextAndResult() {
        AiTask task = new AiTask("ai", "摘要", "summary", "总结：${document}",
                null, null, null, null);
        def = linearDef(task);
        Execution exec = new Execution("e1", "inst-1", "ai");
        instanceRepo.saveExecution(exec);

        runner.registerHandler("default", (system, user, vars) -> Map.of("text", "张三请假五天"));
        assertThat(runner.run(def.getNode("ai"), ctxFor(exec, def))).isTrue();

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("ai_text")).isEqualTo("张三请假五天");
        assertThat(updated.getVariable("ai_result")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) updated.getVariable("ai_result")).get("text")).isEqualTo("张三请假五天");
        assertThat(exec.getCurrentNodeId()).isEqualTo("end");
    }

    @Test
    void templatePlaceholdersAreResolvedFromVariables() {
        AiTask task = new AiTask("ai", "摘要", "summary", "金额 ${amount} 元，${document}",
                null, null, null, null);
        def = linearDef(task);
        Execution exec = new Execution("e2", "inst-1", "ai");
        instanceRepo.saveExecution(exec);

        String[] seen = new String[1];
        runner.registerHandler("default", (system, user, vars) -> {
            seen[0] = user;
            return Map.of("text", "ok");
        });
        runner.run(def.getNode("ai"), ctxFor(exec, def));

        assertThat(seen[0]).isEqualTo("金额 100 元，张三请假5天");
    }

    @Test
    void extractParsesJsonFieldsIntoIndividualVariables() {
        AiTask task = new AiTask("ai", "抽取", "extract", "抽取字段", null, null, null, null);
        def = linearDef(task);
        Execution exec = new Execution("e3", "inst-1", "ai");
        instanceRepo.saveExecution(exec);

        // ServerAiTaskHandler puts parsed JSON keys into the result map; here we
        // emulate that directly (core has no JSON parsing of model output).
        runner.registerHandler("default", (system, user, vars) -> {
            assertThat(system).contains("structured data");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("text", "{\"name\":\"张三\",\"days\":5}");
            r.put("name", "张三");
            r.put("days", 5);
            return r;
        });
        runner.run(def.getNode("ai"), ctxFor(exec, def));

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("ai_name")).isEqualTo("张三");
        assertThat(updated.getVariable("ai_days")).isEqualTo(5);
    }

    @Test
    void classifyWritesCategoryForGatewayRouting() {
        AiTask task = new AiTask("ai", "分类", "classify", "对 ${document} 分类",
                null, List.of("SHORT", "LONG"), null, null);
        // Route on the category — the SpEL gateway use case from the roadmap.
        def = new ProcessDefinition("wf", "Test", 1,
                List.of(task, new EndEvent("short"), new EndEvent("long")),
                List.of(
                    Transition.result("ai", "long", Condition.expression("ai_category == 'LONG'")),
                    Transition.result("ai", "short", null)));
        Execution exec = new Execution("e4", "inst-1", "ai");
        instanceRepo.saveExecution(exec);

        runner.registerHandler("default", (system, user, vars) -> {
            assertThat(system).contains("SHORT, LONG");
            return Map.of("text", " LONG \n");
        });
        runner.run(def.getNode("ai"), ctxFor(exec, def));

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("ai_category")).isEqualTo("LONG");
        assertThat(exec.getCurrentNodeId()).isEqualTo("long");
    }

    @Test
    void outputVarOverridesGlobalVariable() {
        AiTask task = new AiTask("ai", "摘要", "summary", "总结", "analysis",
                null, null, null);
        def = linearDef(task);
        Execution exec = new Execution("e5", "inst-1", "ai");
        instanceRepo.saveExecution(exec);
        runner.registerHandler("default", (s, u, v) -> Map.of("text", "摘要内容"));

        runner.run(def.getNode("ai"), ctxFor(exec, def));

        assertThat(instanceRepo.findById("inst-1").getVariable("analysis")).isInstanceOf(Map.class);
    }

    @Test
    void noHandlerSuspendsWithReason() {
        AiTask task = new AiTask("ai", "摘要", "summary", "总结", null, null, null, null);
        def = linearDef(task);
        Execution exec = new Execution("e6", "inst-1", "ai");
        instanceRepo.saveExecution(exec);
        // no handler registered -> ServiceLoader has none in core tests

        assertThat(runner.run(def.getNode("ai"), ctxFor(exec, def))).isTrue();
        assertThat(exec.getRetryState()).isEqualTo(RetryState.SUSPENDED.name());
        assertThat(instanceRepo.findById("inst-1").getVariable("ai_suspendReason"))
                .asString().contains("AI task failed");
    }

    @Test
    void retryConfigSchedulesBackoff() {
        RetryConfig rc = new RetryConfig(3, 50, 2.0, List.of());
        AiTask task = new AiTask("ai", "摘要", "summary", "总结", null, null, rc, null);
        def = linearDef(task);
        Execution exec = new Execution("e7", "inst-1", "ai");
        instanceRepo.saveExecution(exec);
        List<Long> delays = new ArrayList<>();
        AiTaskRunner retrying = new AiTaskRunner((id, delay) -> delays.add(delay));
        retrying.registerHandler("default", (s, u, v) -> {
            throw new IllegalStateException("provider down");
        });

        assertThat(retrying.run(def.getNode("ai"), ctxFor(exec, def))).isTrue();
        assertThat(exec.getRetryState()).isEqualTo(RetryState.RETRY_PENDING.name());
        assertThat(delays).containsExactly(50L);
    }
}

package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.EndEvent;
import com.github.wf.model.node.ServiceTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaskRunnerTest {

    private com.github.wf.spi.InstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;
    private ProcessDefinition def;

    @BeforeEach
    void setUp() {
        exprEval = new SpelExpressionEvaluator();

        final Map<String, ProcessInstance> instances = new HashMap<>();
        final Map<String, Execution> executions = new HashMap<>();

        instanceRepo = new com.github.wf.spi.InstanceRepository() {
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

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of("amount", 100));
        instanceRepo.save(instance);

        def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ServiceTask("svc", "发通知", "com.test.MyHandler", null),
                        new EndEvent("end")),
                List.of(Transition.direct("svc", "end")));
    }

    @Test
    void invokesHandlerAndMovesToNext() {
        Execution exec = new Execution("e1", "inst-1", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.MyHandler", vars -> {
            int amount = (int) vars.get("amount");
            return Map.of("amount", amount * 2);
        });

        boolean advanced = runner.run(def.getNode("svc"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.getCurrentNodeId()).isEqualTo("end");

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("svc_amount")).isEqualTo(200);
    }

    @Test
    void instantiatesHandlerByClassName() {
        Execution exec = new Execution("e2", "inst-1", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.MyHandler", vars -> Map.of("result", "ok"));

        runner.run(def.getNode("svc"), ctx);

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("svc_result")).isEqualTo(Map.of("result", "ok"));
    }

    @Test
    void resultRoutingMatchesAndRoutesToTargetNode() {
        RoutingRule passRule = RoutingRule.matched(Condition.expression("svc_result['status'] == 'PASS'"), "approved");
        RoutingRule fallback = RoutingRule.defaultRule("rejected");

        ServiceTask svc = new ServiceTask("svc", "风控", "com.test.RiskHandler",
                null, List.of(passRule, fallback), List.of(), null);

        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(svc, new EndEvent("approved"), new EndEvent("rejected")),
                List.of(Transition.direct("svc", "approved")));

        ProcessInstance instance = new ProcessInstance("inst-rr", "wf", Map.of("orderId", "123"));
        instanceRepo.save(instance);

        Execution exec = new Execution("e-rr", "inst-rr", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.RiskHandler", vars -> Map.of("status", "PASS", "score", 85));

        runner.run(def.getNode("svc"), ctx);
        assertThat(exec.getCurrentNodeId()).isEqualTo("approved");
    }

    @Test
    void retryOnMatchingExceptionSchedulesRetry() {
        RetryConfig retryConfig = new RetryConfig(3, 100, 2.0,
                List.of(Condition.expression("exception.message.contains('TimeoutException')")));

        ServiceTask svc = new ServiceTask("svc", "API", "com.test.ApiHandler",
                retryConfig, List.of(), List.of(), null);

        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(svc, new EndEvent("end")),
                List.of(Transition.direct("svc", "end")));

        ProcessInstance instance = new ProcessInstance("inst-retry", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e-retry", "inst-retry", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.ApiHandler", vars -> {
            throw new RuntimeException("TimeoutException: connect timed out");
        });

        runner.run(def.getNode("svc"), ctx);

        assertThat(exec.isWaiting()).isTrue();
        assertThat(exec.getRetryAttempt()).isEqualTo(1);
        assertThat(exec.getCurrentNodeId()).isEqualTo("svc");
    }

    @Test
    void nonMatchingExceptionSkipsRetryAndRoutes() {
        RetryConfig retryConfig = new RetryConfig(3, 100, 2.0,
                List.of(Condition.expression("exception.type.contains('TimeoutException')")));

        RoutingRule fallback = RoutingRule.defaultRule("system-error");

        ServiceTask svc = new ServiceTask("svc", "API", "com.test.ApiHandler",
                retryConfig, List.of(),
                List.of(fallback), null);

        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(svc, new EndEvent("system-error")),
                List.of(Transition.direct("svc", "system-error")));

        ProcessInstance instance = new ProcessInstance("inst-skip", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e-skip", "inst-skip", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.ApiHandler", vars -> {
            throw new IllegalStateException("Business error");
        });

        runner.run(def.getNode("svc"), ctx);

        // Should route to system-error via default exception routing
        assertThat(exec.getCurrentNodeId()).isEqualTo("system-error");
        assertThat(exec.getRetryState()).isNull(); // not suspended
    }

    @Test
    void exhaustRetriesThenExceptionRouting() {
        RetryConfig retryConfig = new RetryConfig(2, 10, 1.0, List.of()); // retry all, max 2

        RoutingRule fallback = RoutingRule.defaultRule("error-end");

        ServiceTask svc = new ServiceTask("svc", "API", "com.test.ApiHandler",
                retryConfig, List.of(),
                List.of(fallback), null);

        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(svc, new EndEvent("error-end")),
                List.of(Transition.direct("svc", "error-end")));

        ProcessInstance instance = new ProcessInstance("inst-exh", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e-exh", "inst-exh", "svc");
        exec.setRetryAttempt(1); // simulate one previous retry
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.ApiHandler", vars -> {
            throw new RuntimeException("API down");
        });

        runner.run(def.getNode("svc"), ctx);

        // After 2nd failure (maxAttempts=2, attempt was 1, now 2 >= 2), exhausts
        assertThat(exec.getCurrentNodeId()).isEqualTo("error-end");
        assertThat(exec.getRetryAttempt()).isEqualTo(0); // reset
    }

    @Test
    void noExceptionRouteMatchSuspendsInstance() {
        ServiceTask svc = new ServiceTask("svc", "API", "com.test.ApiHandler",
                null, List.of(), List.of(), null);

        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(svc, new EndEvent("end")),
                List.of(Transition.direct("svc", "end")));

        ProcessInstance instance = new ProcessInstance("inst-sus", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e-sus", "inst-sus", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.ApiHandler", vars -> {
            throw new RuntimeException("Unexpected");
        });

        runner.run(def.getNode("svc"), ctx);

        assertThat(exec.getRetryState()).isEqualTo("SUSPENDED");
        assertThat(exec.isWaiting()).isTrue();
    }
}

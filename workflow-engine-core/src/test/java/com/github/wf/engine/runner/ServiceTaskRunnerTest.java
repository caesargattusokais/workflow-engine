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
        assertThat(updated.getVariable("amount")).isEqualTo(200);
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
        assertThat(updated.getVariable("result")).isEqualTo("ok");
    }
}

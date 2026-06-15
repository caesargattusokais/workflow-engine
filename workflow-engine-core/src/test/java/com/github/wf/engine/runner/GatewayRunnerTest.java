package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayRunnerTest {

    private com.github.wf.spi.InstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;

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
            public List<Execution> findExecutionsByParentId(String p) {
                return executions.values().stream()
                        .filter(e -> p.equals(e.getParentExecutionId())).toList();
            }
            public void updateExecution(Execution e) { executions.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) {}
            public List<HistoricActivity> findHistory(String i) { return List.of(); }
        };
    }

    @Test
    void exclusiveGatewayChoosesMatchingPath() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ExclusiveGateway("gw"),
                        new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(Transition.conditional("gw", Condition.expression("amount > 100")).withTo("t1"),
                        Transition.defaultTransition("gw", "t2")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of("amount", 200));
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "gw");
        instanceRepo.saveExecution(exec);

        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);
        new ExclusiveGatewayRunner().run(def.getNode("gw"), ctx);
        assertThat(exec.getCurrentNodeId()).isEqualTo("t1");
    }

    @Test
    void exclusiveGatewayFallsThroughToDefault() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ExclusiveGateway("gw"),
                        new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(Transition.conditional("gw", Condition.expression("amount > 100")).withTo("t1"),
                        Transition.defaultTransition("gw", "t2")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of("amount", 50));
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "gw");
        instanceRepo.saveExecution(exec);

        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);
        new ExclusiveGatewayRunner().run(def.getNode("gw"), ctx);
        assertThat(exec.getCurrentNodeId()).isEqualTo("t2");
    }

    @Test
    void parallelGatewayForkCreatesChildExecutions() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ParallelGateway("fork"), new ParallelGateway("join"),
                        new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(Transition.direct("fork", "t1"), Transition.direct("fork", "t2"),
                        Transition.direct("t1", "join"), Transition.direct("t2", "join")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "fork");
        instanceRepo.saveExecution(exec);

        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);
        new ParallelGatewayRunner().run(def.getNode("fork"), ctx);

        assertThat(exec.isWaiting()).isTrue();
        List<Execution> children = instanceRepo.findExecutionsByParentId("e1");
        assertThat(children).hasSize(2);
        assertThat(children).extracting(Execution::getCurrentNodeId)
                .containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void parallelGatewayJoinWaitsForAllSiblings() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ParallelGateway("fork"), new ParallelGateway("join"), new EndEvent("end")),
                List.of(Transition.direct("fork", "t1"), Transition.direct("fork", "t2"),
                        Transition.direct("t1", "join"), Transition.direct("t2", "join"),
                        Transition.direct("join", "end")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of());
        instanceRepo.save(instance);

        Execution parent = new Execution("parent", "inst-1", "fork", null);
        parent.setStatus(ExecutionStatus.WAITING);
        instanceRepo.saveExecution(parent);

        Execution c1 = new Execution("c1", "inst-1", "join", "parent");
        Execution c2 = new Execution("c2", "inst-1", "t2", "parent");
        instanceRepo.saveExecution(c1);
        instanceRepo.saveExecution(c2);

        ParallelGatewayRunner runner = new ParallelGatewayRunner();
        runner.run(def.getNode("join"), new ExecutionContext(def, c1, exprEval, instanceRepo));
        assertThat(instanceRepo.findExecutionById("c1").isWaiting()).isTrue();

        c2.setCurrentNodeId("join");
        instanceRepo.updateExecution(c2);
        runner.run(def.getNode("join"), new ExecutionContext(def, c2, exprEval, instanceRepo));

        assertThat(instanceRepo.findExecutionById("parent").isActive()).isTrue();
    }
}

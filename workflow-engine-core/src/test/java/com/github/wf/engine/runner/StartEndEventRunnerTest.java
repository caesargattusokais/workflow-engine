package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.EndEvent;
import com.github.wf.model.node.StartEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class StartEndEventRunnerTest {

    private InMemoryInstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        exprEval = new SpelExpressionEvaluator();
    }

    @Test
    void startEventMovesToNextNode() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));

        Execution exec = new Execution("inst-1", "start");
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        StartEventRunner runner = new StartEventRunner();
        boolean advanced = runner.run(def.getNode("start"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.getCurrentNodeId()).isEqualTo("end");
    }

    @Test
    void endEventCompletesExecution() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));

        Execution exec = new Execution("inst-1", "end");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        EndEventRunner runner = new EndEventRunner();
        boolean advanced = runner.run(def.getNode("end"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.isCompleted()).isTrue();
    }
}

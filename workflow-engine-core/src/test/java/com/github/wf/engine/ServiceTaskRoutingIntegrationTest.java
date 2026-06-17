package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaskRoutingIntegrationTest {

    private WorkflowEngine engine;
    private final Map<String, ProcessDefinition> processStore = new HashMap<>();
    private final Map<String, ProcessInstance> instanceStore = new HashMap<>();
    private final Map<String, Execution> executionStore = new HashMap<>();
    private final Map<String, Task> taskStore = new HashMap<>();
    private final Map<String, Integer> latestVersion = new HashMap<>();
    private final List<HistoricActivity> historyStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = WorkflowEngine.builder()
                .processRepository(createProcessRepo())
                .instanceRepository(createInstanceRepo())
                .taskRepository(createTaskRepo())
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

    private com.github.wf.spi.ProcessRepository createProcessRepo() {
        return new com.github.wf.spi.ProcessRepository() {
            public void save(ProcessDefinition d) {
                processStore.put(d.getId() + ":" + d.getVersion(), d);
                latestVersion.merge(d.getId(), d.getVersion(), Math::max);
            }
            public ProcessDefinition findById(String id) { return processStore.get(id); }
            public ProcessDefinition findLatestById(String id) {
                Integer v = latestVersion.get(id);
                return v != null ? processStore.get(id + ":" + v) : null;
            }
            public List<ProcessDefinition> findAllVersions(String id) { return List.of(); }
        };
    }

    private com.github.wf.spi.InstanceRepository createInstanceRepo() {
        return new com.github.wf.spi.InstanceRepository() {
            public void save(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public ProcessInstance findById(String id) { return instanceStore.get(id); }
            public void update(ProcessInstance i) { instanceStore.put(i.getId(), i); }
            public List<ProcessInstance> findByDefinitionId(String d) { return List.of(); }
            public void saveExecution(Execution e) { executionStore.put(e.getId(), e); }
            public Execution findExecutionById(String id) { return executionStore.get(id); }
            public List<Execution> findActiveExecutions(String i) {
                return executionStore.values().stream()
                        .filter(e -> e.getInstanceId().equals(i) && !e.isCompleted()).toList();
            }
            public List<Execution> findExecutionsByParentId(String p) {
                return executionStore.values().stream()
                        .filter(e -> p.equals(e.getParentExecutionId())).toList();
            }
            public void updateExecution(Execution e) { executionStore.put(e.getId(), e); }
            public void saveHistoricActivity(HistoricActivity h) { historyStore.add(h); }
            public List<HistoricActivity> findHistory(String i) {
                return historyStore.stream().filter(h -> h.getInstanceId().equals(i)).toList();
            }
        };
    }

    private com.github.wf.spi.TaskRepository createTaskRepo() {
        return new com.github.wf.spi.TaskRepository() {
            public void save(Task t) { taskStore.put(t.getId(), t); }
            public Task findById(String id) { return taskStore.get(id); }
            public void update(Task t) { taskStore.put(t.getId(), t); }
            public List<Task> query(com.github.wf.task.TaskQuery q) {
                return taskStore.values().stream().filter(q::matches).toList();
            }
        };
    }

    @Test
    void resultRoutingViaServiceTask() {
        engine.deploy("""
                id: result-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: check
                    type: serviceTask
                    name: 风控检查
                    handlerClass: "com.test.RiskHandler"
                    resultRouting:
                      - expr: "check_result['risk'] == 'LOW'"
                        to: auto-end
                      - default: true
                        to: manual-end
                  - id: auto-end
                    type: endEvent
                  - id: manual-end
                    type: endEvent
                transitions:
                  - from: start
                    to: check
                """);

        engine.registerServiceHandler("com.test.RiskHandler",
                vars -> Map.of("risk", "LOW", "score", 10));

        ProcessInstance instance = engine.start("result-test", Map.of());
        // Should have completed (routed to auto-end)
        assertThat(instanceStore.get(instance.getId()).getStatus())
                .isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void exceptionRoutingAfterRetryExhaustion() {
        engine.deploy("""
                id: retry-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    name: API调用
                    handlerClass: "com.test.FlakyHandler"
                    retry:
                      maxAttempts: 2
                      delayMs: 0
                      backoffMultiplier: 1
                    exceptionRouting:
                      - default: true
                        to: error-end
                  - id: error-end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                """);

        engine.registerServiceHandler("com.test.FlakyHandler", vars -> {
            throw new RuntimeException("always fails");
        });

        ProcessInstance instance = engine.start("retry-test", Map.of());
        // Should have failed twice, then routed to error-end
        assertThat(instanceStore.get(instance.getId()).getStatus())
                .isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void noRoutingMatchSuspendsInstance() {
        engine.deploy("""
                id: suspend-test
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: call
                    type: serviceTask
                    name: API调用
                    handlerClass: "com.test.BrokenHandler"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: call
                  - from: call
                    to: end
                """);

        engine.registerServiceHandler("com.test.BrokenHandler", vars -> {
            throw new RuntimeException("unexpected failure");
        });

        ProcessInstance instance = engine.start("suspend-test", Map.of());
        // No retry, no exception routing -> SUSPENDED
        assertThat(instanceStore.get(instance.getId()).getStatus())
                .isEqualTo(InstanceStatus.SUSPENDED);

        // Resume
        engine.registerServiceHandler("com.test.BrokenHandler",
                vars -> Map.of("fixed", true));
        engine.resume(instance.getId());

        // Should now succeed
        assertThat(instanceStore.get(instance.getId()).getStatus())
                .isEqualTo(InstanceStatus.COMPLETED);
    }
}

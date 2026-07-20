package com.github.wf.memory;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcProcessRepositoryTest {

    private EmbeddedDatabase db;
    private JdbcProcessRepository repo;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema-h2.sql")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        repo = new JdbcProcessRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ── Helper: minimal definition ──

    private ProcessDefinition simpleDef(String id, int version) {
        return new ProcessDefinition(id, "Test-" + version, version,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));
    }

    private ProcessDefinition gatewayDef(String id, int version) {
        // More complex definition with exclusive gateway + conditional transitions
        Condition cond = Condition.expression("amount > 1000");
        return new ProcessDefinition(id, "Gateway-" + version, version,
                List.of(new StartEvent("start"), new ExclusiveGateway("gw"),
                        new UserTask("approve", "审批", "${manager}", List.of("manager"), null, null, null),
                        new EndEvent("end")),
                List.of(Transition.direct("start", "gw"),
                        Transition.conditional("gw", cond).withTo("approve"),
                        Transition.defaultTransition("gw", "end"),
                        Transition.direct("approve", "end")));
    }

    // ── save + findById ──

    @Test
    void saveAndFindById() {
        ProcessDefinition def = simpleDef("wf-test", 1);
        repo.save(def);

        ProcessDefinition found = repo.findById("wf-test");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo("wf-test");
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.getName()).isEqualTo("Test-1");
    }

    @Test
    void findByIdReturnsLatestVersion() {
        repo.save(simpleDef("wf-multi", 1));
        repo.save(simpleDef("wf-multi", 2));
        repo.save(simpleDef("wf-multi", 3));

        ProcessDefinition found = repo.findById("wf-multi");
        assertThat(found.getVersion()).isEqualTo(3);
    }

    // ── findLatestById ──

    @Test
    void findLatestByIdReturnsHighestVersion() {
        repo.save(simpleDef("wf-latest", 1));
        repo.save(simpleDef("wf-latest", 3));
        repo.save(simpleDef("wf-latest", 2));

        ProcessDefinition latest = repo.findLatestById("wf-latest");
        assertThat(latest).isNotNull();
        assertThat(latest.getVersion()).isEqualTo(3);
    }

    @Test
    void findLatestByIdReturnsNullForUnknown() {
        assertThat(repo.findLatestById("nonexistent")).isNull();
    }

    // ── findByIdAndVersion ──

    @Test
    void findByIdAndVersionReturnsExactVersion() {
        repo.save(simpleDef("wf-ver", 1));
        repo.save(simpleDef("wf-ver", 2));
        repo.save(simpleDef("wf-ver", 3));

        ProcessDefinition v2 = repo.findByIdAndVersion("wf-ver", 2);
        assertThat(v2).isNotNull();
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getName()).isEqualTo("Test-2");
    }

    @Test
    void findByIdAndVersionReturnsNullForMissingVersion() {
        repo.save(simpleDef("wf-ver2", 1));

        assertThat(repo.findByIdAndVersion("wf-ver2", 99)).isNull();
    }

    // ── findAllVersions ──

    @Test
    void findAllVersionsReturnsAllSorted() {
        repo.save(simpleDef("wf-all", 3));
        repo.save(simpleDef("wf-all", 1));
        repo.save(simpleDef("wf-all", 2));

        List<ProcessDefinition> versions = repo.findAllVersions("wf-all");
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(1).getVersion()).isEqualTo(2);
        assertThat(versions.get(2).getVersion()).isEqualTo(3);
    }

    @Test
    void findAllVersionsReturnsEmptyForUnknown() {
        assertThat(repo.findAllVersions("nonexistent")).isEmpty();
    }

    // ── Node serialization round-trip ──

    @Test
    void savesAndLoadsUserTaskWithAllFields() {
        UserTask ut = new UserTask("review", "审批", "${applicant}",
                List.of("manager", "hr"), "com.test.MyRouter", null, null);
        ProcessDefinition def = new ProcessDefinition("wf-ut", "UserTask Test", 1,
                List.of(new StartEvent("start"), ut, new EndEvent("end")),
                List.of(Transition.direct("start", "review"),
                        Transition.direct("review", "end")));
        repo.save(def);

        ProcessDefinition loaded = repo.findLatestById("wf-ut");
        Node node = loaded.getNode("review");
        assertThat(node).isInstanceOf(UserTask.class);
        UserTask loadedUt = (UserTask) node;
        assertThat(loadedUt.getAssignee()).isEqualTo("${applicant}");
        assertThat(loadedUt.getCandidateGroups()).containsExactly("manager", "hr");
        assertThat(loadedUt.getDynamicRouter()).isEqualTo("com.test.MyRouter");
    }

    @Test
    void savesAndLoadsGatewayWithConditionalTransitions() {
        ProcessDefinition def = gatewayDef("wf-gw", 1);
        repo.save(def);

        ProcessDefinition loaded = repo.findLatestById("wf-gw");
        assertThat(loaded.getNodes()).hasSize(4);
        assertThat(loaded.getNode("gw")).isInstanceOf(ExclusiveGateway.class);

        List<Transition> outgoing = loaded.getOutgoingTransitions("gw");
        assertThat(outgoing).hasSize(2);
        assertThat(outgoing.get(0).isConditional()).isTrue();
        assertThat(outgoing.get(0).getCondition().getExpr()).isEqualTo("amount > 1000");
        assertThat(outgoing.get(1).isDefault()).isTrue();
    }

    @Test
    void savesAndLoadsServiceTaskWithRetryAndRouting() {
        RetryConfig rc = new RetryConfig(3, 1000, 2.0, List.of());
        ServiceTask st = new ServiceTask("call", "调用服务", "com.test.Handler",
                false, null, null, null, null, rc,
                List.of(RoutingRule.matched(Condition.expression("result == 'ok'"), "end")),
                List.of(RoutingRule.defaultRule("error-end")),
                null);
        ProcessDefinition def = new ProcessDefinition("wf-st", "ServiceTask Test", 1,
                List.of(new StartEvent("start"), st, new EndEvent("end"), new EndEvent("error-end")),
                List.of(Transition.direct("start", "call")));
        repo.save(def);

        ProcessDefinition loaded = repo.findLatestById("wf-st");
        ServiceTask loadedSt = (ServiceTask) loaded.getNode("call");
        assertThat(loadedSt.getHandlerClass()).isEqualTo("com.test.Handler");
        assertThat(loadedSt.getRetryConfig()).isNotNull();
        assertThat(loadedSt.getRetryConfig().getMaxAttempts()).isEqualTo(3);
        assertThat(loadedSt.getResultRouting()).hasSize(1);
        assertThat(loadedSt.getExceptionRouting()).hasSize(1);
    }

    @Test
    void savesAndLoadsCallActivityNode() {
        CallActivityNode ca = new CallActivityNode("call-child", "子流程", "child-proc", 2,
                List.of(new VariableMapping("applicant", "user", null)),
                List.of(new VariableMapping("result", "approvalResult", null)),
                null, false);
        ProcessDefinition def = new ProcessDefinition("wf-ca", "CallActivity Test", 1,
                List.of(new StartEvent("start"), ca, new EndEvent("end")),
                List.of(Transition.direct("start", "call-child"),
                        Transition.direct("call-child", "end")));
        repo.save(def);

        ProcessDefinition loaded = repo.findLatestById("wf-ca");
        CallActivityNode loadedCa = (CallActivityNode) loaded.getNode("call-child");
        assertThat(loadedCa.getCalledId()).isEqualTo("child-proc");
        assertThat(loadedCa.getCalledVersion()).isEqualTo(2);
        assertThat(loadedCa.getInputMapping()).hasSize(1);
        assertThat(loadedCa.getInputMapping().get(0).getFrom()).isEqualTo("applicant");
        assertThat(loadedCa.getOutputMapping()).hasSize(1);
        assertThat(loadedCa.getOutputMapping().get(0).getTo()).isEqualTo("approvalResult");
    }

    @Test
    void savesAndLoadsTimerNode() {
        TimerNode timer = new TimerNode("wait", "等待", "PT5M", null, null);
        ProcessDefinition def = new ProcessDefinition("wf-timer", "Timer Test", 1,
                List.of(new StartEvent("start"), timer, new EndEvent("end")),
                List.of(Transition.direct("start", "wait"),
                        Transition.direct("wait", "end")));
        repo.save(def);

        ProcessDefinition loaded = repo.findLatestById("wf-timer");
        TimerNode loadedTimer = (TimerNode) loaded.getNode("wait");
        assertThat(loadedTimer.getDuration()).isEqualTo("PT5M");
    }

    // ── Update (upsert) ──

    @Test
    void saveUpdatesExistingVersion() {
        repo.save(simpleDef("wf-upsert", 1));
        ProcessDefinition updated = new ProcessDefinition("wf-upsert", "Updated", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));
        repo.save(updated);

        ProcessDefinition found = repo.findByIdAndVersion("wf-upsert", 1);
        assertThat(found.getName()).isEqualTo("Updated");
    }
}

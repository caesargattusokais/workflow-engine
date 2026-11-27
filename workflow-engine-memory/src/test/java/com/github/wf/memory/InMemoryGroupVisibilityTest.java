package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The memory profile must enforce the same owner-OR-group visibility as JDBC. */
class InMemoryGroupVisibilityTest {

    @Test
    void draftsAreVisibleToOwnerAndGroupMembersOnly() {
        InMemoryDraftRepository repo = new InMemoryDraftRepository();
        Map<String, Object> draft = repo.create("a", "alpha", "shared");
        String id = draft.get("id").toString();

        assertThat(repo.findVisibleById("a", List.of(), id)).isNotNull();
        assertThat(repo.findVisibleById("b", List.of("alpha"), id)).isNotNull();
        assertThat(repo.findVisibleById("c", List.of("beta"), id)).isNull();
        assertThat(repo.findVisibleById("c", List.of(), id)).isNull();
        assertThat(repo.countVisible("b", List.of("alpha"))).isEqualTo(1);
        assertThat(repo.listVisible("c", List.of("beta"))).isEmpty();

        repo.updateName("b", List.of("alpha"), id, "renamed");
        assertThat(repo.findById("a", id).get("name")).isEqualTo("renamed");

        repo.deleteVisible("c", List.of("beta"), id);
        assertThat(repo.findVisibleById("a", List.of("alpha"), id)).isNotNull();
        repo.deleteVisible("b", List.of("alpha"), id);
        assertThat(repo.findVisibleById("a", List.of("alpha"), id)).isNull();
    }

    @Test
    void copiedAndImportedDraftsStayPrivate() {
        InMemoryDraftRepository repo = new InMemoryDraftRepository();
        Map<String, Object> original = repo.create("a", "alpha", "orig");
        Map<String, Object> copy = repo.copy("a", original.get("id").toString(), "copy");
        Map<String, Object> imported = repo.importDraft("a", "imp", List.of(), List.of());

        assertThat(repo.findVisibleById("b", List.of("alpha"), copy.get("id").toString())).isNull();
        assertThat(repo.findVisibleById("b", List.of("alpha"), imported.get("id").toString())).isNull();
    }

    @Test
    void definitionsUseGroupVisibilityAndLatestVersion() {
        InMemoryDefinitionRepository repo = new InMemoryDefinitionRepository();
        Map<String, Map<String, Double>> positions = Map.of("n1", Map.of("x", 1.0, "y", 2.0));
        repo.save("a", "alpha", new ProcessDefinition("def-1", "D", 1, List.of(), List.of()), positions);

        assertThat(repo.findVisibleByUserAndId("b", List.of("alpha"), "def-1")).isNotNull();
        assertThat(repo.findVisibleByUserAndId("c", List.of("beta"), "def-1")).isNull();
        assertThat(repo.findVisiblePositions("b", List.of("alpha"), "def-1", null)).isEqualTo(positions);
        assertThat(repo.findVisiblePositions("b", List.of("alpha"), "def-1", 2)).isNull();
        assertThat(repo.listLatestVisible("b", List.of("alpha"))).hasSize(1);
        assertThat(repo.listLatestVisible("c", List.of("beta"))).isEmpty();

        repo.save("a", "alpha", new ProcessDefinition("def-1", "D", 2, List.of(), List.of()), Map.of());
        assertThat(repo.findVisibleByUserAndId("b", List.of("alpha"), "def-1").getVersion()).isEqualTo(2);
        assertThat(repo.findVisiblePositions("b", List.of("alpha"), "def-1", 2)).isEqualTo(Map.of());
        assertThat(repo.listLatestVisible("b", List.of("alpha"))).hasSize(1);
    }
}

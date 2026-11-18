package com.github.wf.server.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigControllerTest {

    private static javax.sql.DataSource h2() {
        javax.sql.DataSource ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new JdbcTemplate(ds).execute("""
                CREATE TABLE IF NOT EXISTS app_setting (
                  config_key VARCHAR(128) PRIMARY KEY,
                  config_value VARCHAR(2048) NULL,
                  updated_at BIGINT NULL
                )""");
        return ds;
    }

    @Test
    void envFallbackWhenNothingSaved() {
        AiSettingsService svc = new AiSettingsService(null, "http://env.example/v1", "env-key", "env-model");
        var cfg = svc.effective();
        assertThat(cfg).isPresent();
        assertThat(cfg.get().source()).isEqualTo(AiSettings.Source.ENVIRONMENT);
        assertThat(cfg.get().model()).isEqualTo("env-model");
    }

    @Test
    void emptyWhenNothingConfigured() {
        AiSettingsService svc = new AiSettingsService(null, "", "", "");
        assertThat(svc.effective()).isEmpty();
    }

    @Test
    void savePersistsToDatabaseAndOverridesEnv() {
        AiSettingsService svc = new AiSettingsService(h2(), "http://env.example/v1", "env-key", "env-model");
        AiSettings saved = svc.save("http://db.example/v1", "db-key-12345678", "db-model");
        assertThat(saved.source()).isEqualTo(AiSettings.Source.DATABASE);

        var cfg = svc.effective().orElseThrow();
        assertThat(cfg.baseUrl()).isEqualTo("http://db.example/v1");
        assertThat(cfg.model()).isEqualTo("db-model");
        assertThat(cfg.apiKey()).isEqualTo("db-key-12345678");

        svc.clear();
        assertThat(svc.effective().orElseThrow().source()).isEqualTo(AiSettings.Source.ENVIRONMENT);
    }

    @Test
    void saveKeepsExistingKeyWhenBlank() {
        AiSettingsService svc = new AiSettingsService(h2(), "", "", "");
        svc.save("http://x/v1", "secret-abcdef99", "m1");
        AiSettings second = svc.save("http://y/v1", "", "m2");
        assertThat(second.apiKey()).isEqualTo("secret-abcdef99");
        assertThat(second.model()).isEqualTo("m2");
    }

    @Test
    void saveWithoutDataSourceStaysInMemory() {
        AiSettingsService svc = new AiSettingsService(null, "", "", "");
        svc.save("http://mem/v1", "k", "mem-model");
        assertThat(svc.effective().orElseThrow().model()).isEqualTo("mem-model");
        svc.clear();
        assertThat(svc.effective()).isEmpty();
    }

    /**
     * The memory profile still carries the global MySQL datasource, but its
     * schema is never initialised. Saving must not surface a 500 in that case.
     */
    @Test
    void saveFallsBackToMemoryWhenSchemaIsUnusable() {
        javax.sql.DataSource ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        AiSettingsService svc = new AiSettingsService(ds, "", "", "");

        AiSettings saved = svc.save("http://mem/v1", "k", "mem-model");

        assertThat(saved.model()).isEqualTo("mem-model");
        assertThat(svc.effective().orElseThrow().baseUrl()).isEqualTo("http://mem/v1");
        svc.clear();
        assertThat(svc.effective()).isEmpty();
    }

    @Test
    void controllerSaveSucceedsWhenSchemaIsUnusable() {
        javax.sql.DataSource ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        AiSettingsService svc = new AiSettingsService(ds, "", "", "");
        AiConfigController ctrl = new AiConfigController(svc, new DynamicAiClient());

        Map<String, Object> out = ctrl.save(Map.of(
                "baseUrl", "http://mem/v1", "apiKey", "k", "model", "mem-model"));

        assertThat(out.get("baseUrl")).isEqualTo("http://mem/v1");
        assertThat(out.get("model")).isEqualTo("mem-model");
        assertThat(out.get("source")).isEqualTo("database");
    }

    @Test
    void saveValidation() {
        AiSettingsService svc = new AiSettingsService(null, "", "", "");
        assertThatThrownBy(() -> svc.save("", "k", "m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.save("http://x", "k", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maskKeepsOnlyTail() {
        assertThat(AiSettingsService.mask("sk-1234567890abcdef")).isEqualTo("****cdef");
        assertThat(AiSettingsService.mask("short")).isEqualTo("****");
        assertThat(AiSettingsService.mask(null)).isNull();
        assertThat(AiSettingsService.mask("  ")).isNull();
    }

    @Test
    void viewMasksKeyAndReportsSource() {
        AiSettingsService svc = new AiSettingsService(h2(), "", "", "");
        DynamicAiClient client = new DynamicAiClient();
        AiConfigController ctrl = new AiConfigController(svc, client);

        assertThat(ctrl.view().get("source")).isEqualTo("none");
        assertThat(ctrl.view().get("configured")).isEqualTo(false);

        svc.save("http://x/v1", "sk-1234567890abcdef", "m");
        client.setDelegate(svc.effective().orElseThrow().newClient());
        Map<String, Object> view = ctrl.view();
        assertThat(view.get("configured")).isEqualTo(true);
        assertThat(view.get("source")).isEqualTo("database");
        assertThat(view.get("apiKeyMasked")).isEqualTo("****cdef");
        assertThat(view.get("baseUrl")).isEqualTo("http://x/v1");
    }

    @Test
    void testRequiresBaseUrlAndModel() {
        AiConfigController ctrl = new AiConfigController(
                new AiSettingsService(null, "", "", ""), new DynamicAiClient());
        Map<String, Object> out = ctrl.test(Map.of());
        assertThat(out.get("ok")).isEqualTo(false);
    }

    @Test
    void testReportsUnreachableProvider() {
        AiConfigController ctrl = new AiConfigController(
                new AiSettingsService(null, "", "", ""), new DynamicAiClient());
        Map<String, Object> out = ctrl.test(Map.of(
                "baseUrl", "http://127.0.0.1:1/v1", "apiKey", "k", "model", "m"));
        assertThat(out.get("ok")).isEqualTo(false);
        assertThat((String) out.get("message"))
                .contains("无法连接")
                .contains("/v1/chat/completions");
    }

    @Test
    void dynamicClientThrowsWhenUnconfigured() {
        DynamicAiClient client = new DynamicAiClient();
        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI is not configured");

        client.setDelegate((sys, user) -> "ok");
        assertThat(client.isConfigured()).isTrue();
        assertThat(client.complete("s", "u")).isEqualTo("ok");
    }

    @Test
    void generateUsesLatestDelegateAfterRepoint() {
        AiSettingsService svc = new AiSettingsService(null, "", "", "");
        DynamicAiClient client = new DynamicAiClient();
        AiController ai = new AiController(client);

        // Unconfigured → standard error
        assertThatThrownBy(() -> ai.generate("u1", Map.of("prompt", "x")))
                .isInstanceOf(IllegalStateException.class);

        // "Page save" re-points the delegate → generate works immediately, no restart
        svc.save("http://stub/v1", "k", "stub-model");
        client.setDelegate((sys, user) -> STUB_YAML);
        Map<String, Object> out = ai.generate("u1", Map.of("prompt", "随便"));
        assertThat(((String) out.get("yaml")).strip()).isEqualTo(STUB_YAML.strip());
    }

    private static final String STUB_YAML = """
            id: f
            name: 'f'
            version: 1
            nodes:
              - id: start
                type: startEvent
              - id: end
                type: endEvent
            transitions:
              - from: start
                to: end
            """
            .stripIndent();
}

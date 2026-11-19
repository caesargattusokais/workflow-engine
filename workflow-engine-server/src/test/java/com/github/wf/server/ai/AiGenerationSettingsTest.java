package com.github.wf.server.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGenerationSettingsTest {

    @Test
    void defaultsWhenNothingConfigured() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);
        AiGenerationSettings.Config cfg = svc.effective();

        assertThat(cfg.isDefaultPrompt()).isTrue();
        assertThat(cfg.temperature()).isEqualTo(0.2);
        assertThat(cfg.source()).isEqualTo(AiGenerationSettings.Source.DEFAULT);
    }

    @Test
    void saveKeepsCustomPromptAndTemperature() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);

        AiGenerationSettings.Config cfg = svc.save("只用 userTask 节点", 0.7);

        assertThat(cfg.systemPrompt()).isEqualTo("只用 userTask 节点");
        assertThat(cfg.temperature()).isEqualTo(0.7);
        assertThat(cfg.isDefaultPrompt()).isFalse();
        assertThat(svc.effective().temperature()).isEqualTo(0.7);
    }

    @Test
    void blankPromptRestoresBuiltInDefault() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);
        svc.save("custom", 0.9);

        AiGenerationSettings.Config cfg = svc.save("  ", 0.4);

        assertThat(cfg.isDefaultPrompt()).isTrue();
        assertThat(cfg.temperature()).isEqualTo(0.4);
    }

    @Test
    void clearReturnsToDefaults() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);
        svc.save("custom", 1.5);

        svc.clear();

        assertThat(svc.effective().isDefaultPrompt()).isTrue();
        assertThat(svc.effective().temperature()).isEqualTo(0.2);
    }

    @Test
    void envValuesUsedWhenNoPageOverride() {
        AiGenerationSettings svc = new AiGenerationSettings(null, "env prompt", "0.8");

        AiGenerationSettings.Config cfg = svc.effective();

        assertThat(cfg.systemPrompt()).isEqualTo("env prompt");
        assertThat(cfg.temperature()).isEqualTo(0.8);
        assertThat(cfg.source()).isEqualTo(AiGenerationSettings.Source.ENVIRONMENT);
    }

    @Test
    void invalidTemperatureRejected() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);
        assertThatThrownBy(() -> svc.save("p", 3.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> svc.save("p", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void controllerReturnsBuiltInDefaultPromptWhenUnset() {
        AiGenerationController ctrl = new AiGenerationController(
                new AiGenerationSettings(null, null, null), wiringNoop());

        Map<String, Object> view = ctrl.view();

        assertThat(view.get("systemPrompt")).isEqualTo(AiController.DEFAULT_SYSTEM_PROMPT);
        assertThat(view.get("isDefaultPrompt")).isEqualTo(true);
        assertThat(view.get("temperature")).isEqualTo(0.2);
    }

    @Test
    void controllerSaveThenReset() {
        AiGenerationSettings svc = new AiGenerationSettings(null, null, null);
        AiGenerationController ctrl = new AiGenerationController(svc, wiringNoop());

        Map<String, Object> saved = ctrl.save(Map.of("systemPrompt", "custom prompt", "temperature", 0.6));
        assertThat(saved.get("systemPrompt")).isEqualTo("custom prompt");
        assertThat(saved.get("temperature")).isEqualTo(0.6);
        assertThat(saved.get("source")).isEqualTo("database");

        Map<String, Object> reset = ctrl.reset();
        assertThat(reset.get("isDefaultPrompt")).isEqualTo(true);
        assertThat(reset.get("temperature")).isEqualTo(0.2);
    }

    @Test
    void customPromptIsActuallySentToModel() {
        AtomicReference<String> seenSystem = new AtomicReference<>();
        AiClient recording = (sys, user) -> {
            seenSystem.set(sys);
            return "id: x\nname: 'x'\nversion: 1\nnodes:\n  - id: start\n    type: startEvent\n  - id: end\n    type: endEvent\ntransitions:\n  - from: start\n    to: end";
        };
        AiGenerationSettings gen = new AiGenerationSettings(null, null, null);
        gen.save("只输出两个节点的极简流程", 0.5);
        AiController ctrl = new AiController(recording, gen);

        ctrl.generate("u1", Map.of("prompt", "任意"));

        assertThat(seenSystem.get()).isEqualTo("只输出两个节点的极简流程");
    }

    /** Controller tests don't need a live delegate; repoint is a no-op here. */
    private static AiClientWiring wiringNoop() {
        return new AiClientWiring(
                new AiSettingsService(null, "", "", ""),
                new AiGenerationSettings(null, null, null),
                new DynamicAiClient());
    }
}

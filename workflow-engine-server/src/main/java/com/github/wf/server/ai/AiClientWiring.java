package com.github.wf.server.ai;

import org.springframework.stereotype.Component;

/**
 * Single place that (re)points {@link DynamicAiClient}. Both provider settings
 * and generation settings can change at runtime; either change rebuilds the
 * delegate from the currently effective combination of the two, so saving a
 * prompt never silently resets a custom temperature (or vice versa).
 */
@Component
public class AiClientWiring {

    private final AiSettingsService provider;
    private final AiGenerationSettings generation;
    private final DynamicAiClient client;

    public AiClientWiring(AiSettingsService provider, AiGenerationSettings generation, DynamicAiClient client) {
        this.provider = provider;
        this.generation = generation;
        this.client = client;
    }

    /** Rebuild the delegate from the effective provider + generation settings. */
    public void repoint() {
        AiGenerationSettings.Config gen = generation.effective();
        client.setDelegate(provider.effective()
                .map(settings -> settings.newClient(gen.temperature()))
                .orElse(null));
    }
}

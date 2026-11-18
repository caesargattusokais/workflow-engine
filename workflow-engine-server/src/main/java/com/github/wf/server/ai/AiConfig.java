package com.github.wf.server.ai;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * AI provider wiring. A {@link DynamicAiClient} is always registered and
 * initially pointed at whatever {@link AiSettingsService} resolves
 * (page-configured DB values first, then AI_BASE_URL env defaults).
 * Saving new settings from the configuration page re-points it at runtime
 * — no restart needed. When nothing is configured, /api/ai/* degrades
 * gracefully to a 409.
 */
@Configuration
public class AiConfig {

    @Bean
    public AiSettingsService aiSettingsService(ObjectProvider<DataSource> dataSource,
            @Value("${ai.base-url:}") String baseUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:deepseek-chat}") String model) {
        return new AiSettingsService(dataSource.getIfAvailable(), baseUrl, apiKey, model);
    }

    @Bean
    public DynamicAiClient aiClient(AiSettingsService settings) {
        DynamicAiClient client = new DynamicAiClient();
        settings.effective().ifPresent(cfg -> client.setDelegate(cfg.newClient()));
        return client;
    }
}

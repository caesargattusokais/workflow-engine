package com.github.wf.server.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI provider wiring. The bean is only created when {@code ai.base-url}
 * (env: AI_BASE_URL) is set — the rest of the app degrades gracefully to a
 * 409 on /api/ai/* when it is absent.
 */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty("ai.base-url")
    public AiClient aiClient(@Value("${ai.base-url}") String baseUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:deepseek-chat}") String model) {
        return new OpenAiCompatibleAiClient(baseUrl, apiKey, model);
    }
}

package com.github.wf.server.ai;

/**
 * AI settings value object. {@code apiKey} may be null (some local
 * providers need no key). {@code source} records where the effective
 * values came from.
 */
public record AiSettings(String baseUrl, String apiKey, String model, Source source) {

    public enum Source { DATABASE, ENVIRONMENT }

    public OpenAiCompatibleAiClient newClient() {
        return new OpenAiCompatibleAiClient(baseUrl, apiKey == null ? "" : apiKey, model);
    }
}

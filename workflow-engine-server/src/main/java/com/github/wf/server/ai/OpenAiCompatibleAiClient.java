package com.github.wf.server.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat-completions client (works with OpenAI, DeepSeek,
 * Qwen/DashScope-compatible mode, Ollama, vLLM, …).
 */
public class OpenAiCompatibleAiClient implements AiClient {

    private final RestClient rest;
    private final String model;

    public OpenAiCompatibleAiClient(String baseUrl, String apiKey, String model) {
        this.model = model;
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rest = RestClient.builder()
                .baseUrl(root)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2);

        Map<String, Object> resp = rest.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null) throw new IllegalStateException("Empty response from AI provider");
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> err) {
            throw new IllegalStateException("AI provider error: " + err.get("message"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI provider returned no choices");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message != null ? message.get("content") : null;
        if (content == null || content.toString().isBlank()) {
            throw new IllegalStateException("AI provider returned empty content");
        }
        return content.toString();
    }

    @Override
    public String describe() {
        return "OpenAI-compatible[" + model + "]";
    }
}

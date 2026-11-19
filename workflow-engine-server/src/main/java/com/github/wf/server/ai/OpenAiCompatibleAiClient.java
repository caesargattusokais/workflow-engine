package com.github.wf.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI-compatible chat-completions client (works with OpenAI, DeepSeek,
 * Qwen/DashScope-compatible mode, Ollama, vLLM, …).
 *
 * <p>Two vendor realities are handled here so callers do not have to care:
 * <ul>
 *   <li><b>Reasoning models reject {@code temperature}.</b> The o-series and
 *       the GPT-5 family answer a non-default temperature with
 *       {@code 400 Unsupported value: 'temperature' ...}. Rather than guessing
 *       from the model name (which lies — {@code gpt-5-chat-latest} still
 *       accepts it), the first rejection is detected from the response body and
 *       the call is retried once without the parameter.</li>
 *   <li><b>Every provider is a different URL shape.</b> Failures carry the
 *       concrete endpoint that was called, plus a hint when a 404 looks like a
 *       missing {@code /v1} version segment.</li>
 * </ul>
 */
public class OpenAiCompatibleAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    static final String CHAT_PATH = "/chat/completions";

    private final RestClient rest;
    private final String model;
    private final String baseUrl;
    private final String endpoint;

    public OpenAiCompatibleAiClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        if (this.baseUrl.isEmpty()) {
            throw new IllegalArgumentException("AI baseUrl is required");
        }
        this.endpoint = this.baseUrl + CHAT_PATH;
        this.model = model;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        // Local providers (Ollama, vLLM) run without a key — sending an empty
        // "Bearer " header makes some of them reject the request.
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.rest = builder.build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            return extract(call(buildBody(systemPrompt, userPrompt, true)));
        } catch (ProviderHttpException e) {
            if (!e.looksLikeTemperatureRejection()) throw e;
            log.warn("{} rejected temperature ({}); retrying without it",
                    describe(), e.shortReason());
            return extract(call(buildBody(systemPrompt, userPrompt, false)));
        }
    }

    private Map<String, Object> call(Map<String, Object> body) {
        try {
            return rest.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw new ProviderHttpException(endpoint, model, e);
        }
    }

    private Map<String, Object> buildBody(String systemPrompt, String userPrompt, boolean withTemperature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)));
        if (withTemperature) body.put("temperature", 0.2);
        return body;
    }

    @SuppressWarnings("unchecked")
    private String extract(Map<String, Object> resp) {
        if (resp == null) {
            throw new IllegalStateException("Empty response body from POST " + endpoint + " (model=" + model + ")");
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> err) {
            throw new IllegalStateException("Provider error from " + endpoint + ": " + err.get("message"));
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Provider returned no choices from " + endpoint + " (model=" + model + ")");
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        Object content = message != null ? message.get("content") : null;
        if (content == null || content.toString().isBlank()) {
            // Reasoning models can burn the whole output budget on hidden
            // reasoning tokens, leaving content empty — surface finish_reason
            // so the failure is diagnosable instead of a bare "empty content".
            Object finish = choice.get("finish_reason");
            throw new IllegalStateException("Provider returned empty content from " + endpoint
                    + " (model=" + model + (finish != null ? ", finish_reason=" + finish : "") + ")");
        }
        return content.toString();
    }

    @Override
    public String describe() {
        return "OpenAI-compatible[" + model + "@" + baseUrl + "]";
    }

    /** Endpoint that will be called — surfaced in the settings page test result. */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Trim, drop trailing slashes, and strip a {@code /chat/completions} suffix
     * users often paste along with the base URL.
     */
    static String normalizeBaseUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.length() > CHAT_PATH.length() && s.endsWith(CHAT_PATH)) {
            s = s.substring(0, s.length() - CHAT_PATH.length());
            while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** True when the URL path ends in a version segment such as {@code /v1} or {@code /api/paas/v4}. */
    static boolean hasVersionSuffix(String baseUrl) {
        String s = normalizeBaseUrl(baseUrl);
        int scheme = s.indexOf("://");
        String rest = scheme >= 0 ? s.substring(scheme + 3) : s;
        int pathStart = rest.indexOf('/');
        if (pathStart < 0) return false;
        return rest.substring(pathStart).matches("(?s).*/v\\d+[A-Za-z0-9]*$");
    }

    /** Provider answered with an HTTP error status. */
    static final class ProviderHttpException extends RuntimeException {

        private final int status;
        private final String responseBody;
        private final String reason;

        ProviderHttpException(String endpoint, String model, HttpStatusCodeException cause) {
            super(buildMessage(endpoint, model, cause.getStatusCode().value(), cause.getResponseBodyAsString()),
                    cause);
            this.status = cause.getStatusCode().value();
            this.responseBody = cause.getResponseBodyAsString();
            this.reason = providerReason(this.responseBody);
        }

        int status() {
            return status;
        }

        String responseBody() {
            return responseBody;
        }

        String shortReason() {
            return reason != null ? reason : "HTTP " + status;
        }

        /**
         * o-series and GPT-5 reject sampling parameters outright. Other 400s
         * (bad model name, malformed body) mention different fields and must
         * not trigger the retry.
         */
        boolean looksLikeTemperatureRejection() {
            if (status != 400 && status != 422) return false;
            String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
            return body.contains("temperature");
        }

        private static String buildMessage(String endpoint, String model, int status, String body) {
            StringBuilder sb = new StringBuilder()
                    .append("HTTP ").append(status)
                    .append(" from POST ").append(endpoint)
                    .append(" (model=").append(model).append(")");
            String reason = providerReason(body);
            if (reason != null) sb.append(" — ").append(reason);
            if (status == 404 && !hasVersionSuffix(endpoint)) {
                sb.append(" 【提示】该 baseUrl 没有版本段，多数服务商要求末尾带 /v1（如 https://api.deepseek.com/v1）");
            }
            return sb.toString();
        }

        /**
         * Pull {@code error.message} out of the body — provider error envelopes
         * differ, but this is the one field nearly all of them agree on.
         */
        @SuppressWarnings("unchecked")
        static String providerReason(String body) {
            if (body == null || body.isBlank()) return null;
            try {
                Map<String, Object> parsed = JSON.readValue(body, Map.class);
                Object error = parsed.get("error");
                if (error instanceof Map<?, ?> err) {
                    Object msg = err.get("message");
                    if (msg != null && !msg.toString().isBlank()) return msg.toString();
                }
                Object msg = parsed.get("message");
                if (msg != null && !msg.toString().isBlank()) return msg.toString();
            } catch (Exception ignored) {
                // Not JSON — fall through to the raw body.
            }
            String trimmed = body.strip();
            return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "…";
        }
    }
}

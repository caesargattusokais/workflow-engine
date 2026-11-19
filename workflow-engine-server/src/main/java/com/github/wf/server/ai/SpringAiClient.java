package com.github.wf.server.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Spring AI backed {@link AiClient}. Speaks the OpenAI-compatible
 * chat/completions protocol through Spring's {@link OpenAiChatModel}, so the
 * engine keeps one SPI while the transport, JSON mapping and future
 * tool-calling / structured-output work come from Spring AI.
 *
 * <p>The model is built per instance (not a startup bean) because the provider
 * is configured at runtime from the settings page — Spring AI's starter would
 * try to auto-configure speech/image/embedding models at boot and fail when no
 * key exists yet.
 */
public class SpringAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiClient.class);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    private final String baseUrl;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final OpenAiChatModel withTemperature;
    private final OpenAiChatModel withoutTemperature;

    static final double DEFAULT_TEMPERATURE = 0.2;

    public SpringAiClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, DEFAULT_TEMPERATURE);
    }

    public SpringAiClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = OpenAiCompatibleAiClient.normalizeBaseUrl(baseUrl);
        if (this.baseUrl.isEmpty()) {
            throw new IllegalArgumentException("AI baseUrl is required");
        }
        this.endpoint = this.baseUrl + OpenAiCompatibleAiClient.CHAT_PATH;
        this.model = model;
        this.apiKey = apiKey;

        OpenAiApi api = buildApi();
        this.withTemperature = buildModel(api, AiGenerationSettings.validateTemperature(temperature));
        this.withoutTemperature = buildModel(api, null);
    }

    private OpenAiApi buildApi() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder rest = RestClient.builder().requestFactory(factory);
        // Spring AI defaults completionsPath to "/v1/chat/completions"; our
        // baseUrl already carries the version segment, so pin it to avoid /v1/v1.
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .completionsPath(OpenAiCompatibleAiClient.CHAT_PATH)
                .apiKey(apiKey == null || apiKey.isBlank()
                        ? new NoopApiKey() : new org.springframework.ai.model.SimpleApiKey(apiKey))
                .restClientBuilder(rest)
                .responseErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler())
                .build();
    }

    private OpenAiChatModel buildModel(OpenAiApi api, Double temperature) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(model);
        if (temperature != null) options.temperature(temperature);
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                // Spring AI retries 3x by default, including on 400/401. Our
                // temperature fallback must own the retry count: one retry
                // without the parameter, nothing else.
                .retryTemplate(org.springframework.retry.support.RetryTemplate.builder()
                        .maxAttempts(1).build())
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt == null ? "" : systemPrompt),
                new UserMessage(userPrompt == null ? "" : userPrompt)));
        try {
            return extract(withTemperature.call(prompt));
        } catch (Exception e) {
            if (!looksLikeTemperatureRejection(e)) throw translate(e);
            log.warn("{} rejected temperature; retrying without it", describe());
            return extract(withoutTemperature.call(prompt));
        }
    }

    private String extract(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Provider returned no choices from " + endpoint + " (model=" + model + ")");
        }
        AssistantMessage message = response.getResult().getOutput();
        String content = message != null ? message.getText() : null;
        if (content == null || content.isBlank()) {
            Object finish = response.getResult().getMetadata() != null
                    ? response.getResult().getMetadata().getFinishReason() : null;
            throw new IllegalStateException("Provider returned empty content from " + endpoint
                    + " (model=" + model + (finish != null ? ", finish_reason=" + finish : "") + ")");
        }
        return content;
    }

    /**
     * Reasoning models (o-series, GPT-5) answer a non-default temperature with
     * 400. Detect from the response body rather than the model name —
     * {@code gpt-5-chat-latest} still accepts the parameter.
     */
    static boolean looksLikeTemperatureRejection(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof HttpStatusCodeException http
                    && (http.getStatusCode().value() == 400 || http.getStatusCode().value() == 422)) {
                String body = http.getResponseBodyAsString().toLowerCase(Locale.ROOT);
                if (body.contains("temperature")) return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("temperature")
                    && msg.toLowerCase(Locale.ROOT).contains("unsupported")) return true;
            current = current.getCause();
        }
        return false;
    }

    /** Wrap framework exceptions with the concrete endpoint for diagnostics. */
    private RuntimeException translate(Exception e) {
        if (e instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            String body = http.getResponseBodyAsString();
            StringBuilder sb = new StringBuilder()
                    .append("HTTP ").append(status)
                    .append(" from POST ").append(endpoint)
                    .append(" (model=").append(model).append(")");
            String reason = OpenAiCompatibleAiClient.ProviderHttpException.providerReason(body);
            if (reason != null) sb.append(" — ").append(reason);
            if (status == 404 && !OpenAiCompatibleAiClient.hasVersionSuffix(endpoint)) {
                sb.append(" 【提示】该 baseUrl 没有版本段，多数服务商要求末尾带 /v1（如 https://api.deepseek.com/v1）");
            }
            return new IllegalStateException(sb.toString(), e);
        }
        if (e instanceof IllegalArgumentException) return (IllegalArgumentException) e;
        return new IllegalStateException("AI call failed for POST " + endpoint
                + " (model=" + model + "): " + e.getMessage(), e);
    }

    @Override
    public String describe() {
        return "SpringAI[" + model + "@" + baseUrl + "]";
    }

    /** Endpoint that will be called — surfaced in the settings page test result. */
    public String endpoint() {
        return endpoint;
    }
}

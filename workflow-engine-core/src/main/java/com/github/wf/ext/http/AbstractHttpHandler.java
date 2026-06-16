package com.github.wf.ext.http;

import com.github.wf.ext.ServiceTaskHandler;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Base class for HTTP-based service task handlers.
 * <p>
 * Subclasses need only implement {@link #getUrl(Map)} to define the target API.
 * Override other methods to customize method, headers, body, timeout, or response parsing.
 * <p>
 * The engine's retry/routing SpEL can match:
 * <ul>
 *   <li>{@code exception.type.contains('HttpTimeoutException')} — connection/read timeout</li>
 *   <li>{@code exception.type.contains('HttpStatusException')} — non-2xx status</li>
 *   <li>{@code exception.type.contains('IOException')} — network error</li>
 * </ul>
 */
public abstract class AbstractHttpHandler implements ServiceTaskHandler {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    // ── Subclass overrides ──────────────────────────────────

    /** Target URL. Required. */
    protected abstract String getUrl(Map<String, Object> variables);

    /** HTTP method. Default POST. */
    protected String getMethod() { return "POST"; }

    /** Request headers. Default: Content-Type=application/json. */
    protected Map<String, String> getHeaders(Map<String, Object> variables) {
        return Map.of("Content-Type", "application/json");
    }

    /** Request body. Default: JSON-serialized variables. */
    protected String getBody(Map<String, Object> variables) {
        return GSON.toJson(variables);
    }

    /** Connection + read timeout. Default 30s. */
    protected Duration getTimeout() { return Duration.ofSeconds(30); }

    /**
     * Parse the HTTP response. Default: parse body as JSON Map.
     * Override for XML, plain text, or custom logic.
     */
    protected Map<String, Object> parseResponse(HttpResponse<String> response,
                                                 Map<String, Object> variables) {
        if (response.body() == null || response.body().isBlank()) {
            return Map.of("statusCode", response.statusCode());
        }
        Map<String, Object> result = GSON.fromJson(response.body(), MAP_TYPE);
        result.put("statusCode", response.statusCode());
        return result;
    }

    // ── Engine entry point ──────────────────────────────────

    @Override
    public Map<String, Object> execute(Map<String, Object> variables) {
        HttpClient client = buildClient();
        HttpRequest request = buildRequest(variables);

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new HttpTimeoutException("HTTP timeout: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e); // preserves IOException in type chain
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request interrupted", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, response.body());
        }

        return parseResponse(response, variables);
    }

    // ── Internal ────────────────────────────────────────────

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .connectTimeout(getTimeout())
                .build();
    }

    private HttpRequest buildRequest(Map<String, Object> variables) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(getUrl(variables)))
                .timeout(getTimeout());

        getHeaders(variables).forEach(builder::header);

        String method = getMethod().toUpperCase();
        String body = getBody(variables);

        if (body != null && !body.isEmpty()
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }
}

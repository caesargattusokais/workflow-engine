package com.github.wf.ext.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Shared HTTP utility for ServiceTask HTTP proxy and UserTask HTTP callback.
 */
public final class HttpClientUtil {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Execute an HTTP request and return the parsed JSON response.
     */
    public static Map<String, Object> execute(String url, String method,
                                               Map<String, String> headers, String body,
                                               Map<String, Object> variables) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(interpolate(url, variables)))
                    .timeout(Duration.ofSeconds(30));

            if (headers != null) {
                for (var entry : headers.entrySet()) {
                    builder.header(entry.getKey(), interpolate(entry.getValue(), variables));
                }
            }

            String m = method != null ? method.toUpperCase() : "POST";
            String bodyStr = body != null ? interpolate(body, variables) : null;

            if (bodyStr != null && !bodyStr.isEmpty()
                    && ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m))) {
                builder.method(m, HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
            } else {
                builder.method(m, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + ": " + response.body());
            }

            if (response.body() == null || response.body().isBlank()) {
                return Map.of("statusCode", status, "body", "");
            }

            Gson gson = new Gson();
            var type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> result = gson.fromJson(response.body(), type);
            result.put("statusCode", status);
            return result;

        } catch (java.net.http.HttpTimeoutException e) {
            throw new HttpTimeoutException("HTTP timeout: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP interrupted", e);
        }
    }

    /** Simple ${var} interpolation in strings */
    public static String interpolate(String template, Map<String, Object> vars) {
        if (template == null) return null;
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    /** Fire-and-forget HTTP request — no response parsing, just ensure 2xx */
    public static void fireAndForget(String url, String method,
                                      Map<String, String> headers, String body,
                                      Map<String, Object> variables) {
        execute(url, method, headers, body, variables);
    }
}

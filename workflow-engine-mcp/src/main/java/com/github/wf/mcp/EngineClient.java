package com.github.wf.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin REST client for the workflow engine's /api. Every call carries the
 * configured X-User-Id so the engine's tenant filtering applies to MCP
 * operations exactly as it does for human users.
 */
@Component
public class EngineClient {

    private final RestClient rest;
    private final String userId;

    public EngineClient(
            @Value("${wf.engine.base-url:http://localhost:8080/api}") String baseUrl,
            @Value("${wf.engine.user:mcp-agent}") String userId,
            @Value("${wf.engine.token:}") String token) {
        this.userId = userId;
        var builder = RestClient.builder().baseUrl(baseUrl);
        if (token != null && !token.isBlank()) {
            // Preferred: authenticate as a real session token. Works even when
            // the engine disables the legacy X-User-Id compatibility channel.
            builder.defaultHeader("Authorization", "Bearer " + token.trim());
        } else {
            // Backward compatibility for deployments that opted into header auth.
            builder.defaultHeader("X-User-Id", userId);
        }
        this.rest = builder.build();
    }

    public String getUserId() { return userId; }

    /** GET returning parsed JSON (map or array), or throwing with the engine's error message. */
    public Object get(String path) {
        try {
            return rest.get().uri(path).retrieve().body(Object.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("GET " + path + " failed (" + e.getStatusCode().value() + "): " + errorBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Engine unreachable at GET " + path + ": " + e.getMessage(), e);
        }
    }

    /** POST with a JSON body; body may be null for empty posts. */
    public Object post(String path, Object body) {
        try {
            var spec = rest.post().uri(path);
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return spec.retrieve().body(Object.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("POST " + path + " failed (" + e.getStatusCode().value() + "): " + errorBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Engine unreachable at POST " + path + ": " + e.getMessage(), e);
        }
    }

    /** DELETE; returns a short confirmation string. */
    public String delete(String path) {
        try {
            rest.delete().uri(path).retrieve().toBodilessEntity();
            return "deleted: " + path;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("DELETE " + path + " failed (" + e.getStatusCode().value() + "): " + errorBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Engine unreachable at DELETE " + path + ": " + e.getMessage(), e);
        }
    }

    /** Extract the engine's own error message from the response body if present. */
    private static String errorBody(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) return "(no body)";
        // Body is JSON like {"error":"..."} — pass it through compactly.
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }
}

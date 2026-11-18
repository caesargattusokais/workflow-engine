package com.github.wf.server.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-level behaviour against a local stub provider: request shape,
 * reasoning-model parameter fallback, base URL normalisation and the
 * diagnostics inside failure messages.
 */
class OpenAiCompatibleAiClientTest {

    private record Stub(int status, String body) {}

    private HttpServer server;
    private String base;

    private final Queue<Stub> scripted = new ConcurrentLinkedQueue<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));

            Stub stub = scripted.poll();
            if (stub == null) stub = new Stub(500, "{\"error\":{\"message\":\"no scripted response\"}}");
            byte[] payload = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void sendsTemperatureAndParsesContent() {
        scripted.add(ok("hello"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "sk-test", "deepseek-chat");

        assertThat(client.complete("be brief", "hi")).isEqualTo("hello");
        assertThat(paths).containsExactly("/v1/chat/completions");
        assertThat(bodies.get(0))
                .contains("\"temperature\":0.2")
                .contains("\"model\":\"deepseek-chat\"")
                .contains("be brief");
        assertThat(authHeaders.get(0)).isEqualTo("Bearer sk-test");
    }

    @Test
    void omitsAuthorizationHeaderWhenNoKey() {
        scripted.add(ok("hello"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "  ", "llama3");

        assertThat(client.complete("s", "u")).isEqualTo("hello");
        assertThat(authHeaders.get(0)).isNull();
    }

    // ------------------------------------------------- reasoning-model fallback

    @Test
    void retriesWithoutTemperatureWhenModelRejectsIt() {
        scripted.add(new Stub(400, """
                {"error":{"message":"Unsupported value: 'temperature' does not support 0.2 with this model. \
                Only the default (1) value is supported.","type":"invalid_request_error","param":"temperature"}}"""));
        scripted.add(ok("hello"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "sk-test", "gpt-5");

        assertThat(client.complete("s", "u")).isEqualTo("hello");
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("temperature");
        assertThat(bodies.get(1)).doesNotContain("temperature");
        assertThat(bodies.get(1)).contains("\"model\":\"gpt-5\"");
    }

    @Test
    void doesNotRetryUnrelatedBadRequest() {
        scripted.add(new Stub(400, "{\"error\":{\"message\":\"model not found: nope\",\"param\":\"model\"}}"));
        scripted.add(ok("should-not-be-used"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "sk-test", "nope");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(OpenAiCompatibleAiClient.ProviderHttpException.class)
                .hasMessageContaining("model not found: nope");
        assertThat(bodies).hasSize(1);
    }

    @Test
    void surfacesUnauthorizedAsProviderMessage() {
        scripted.add(new Stub(401, "{\"error\":{\"message\":\"invalid api key\"}}"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "bad", "m");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("invalid api key");
    }

    // ------------------------------------------------------- base URL handling

    @Test
    void normalizesBaseUrl() {
        assertThat(OpenAiCompatibleAiClient.normalizeBaseUrl("https://api.x.com/v1/"))
                .isEqualTo("https://api.x.com/v1");
        assertThat(OpenAiCompatibleAiClient.normalizeBaseUrl("https://api.x.com/v1/chat/completions"))
                .isEqualTo("https://api.x.com/v1");
        assertThat(OpenAiCompatibleAiClient.normalizeBaseUrl("  https://api.x.com/v1  "))
                .isEqualTo("https://api.x.com/v1");
        assertThat(OpenAiCompatibleAiClient.normalizeBaseUrl(null)).isEmpty();
    }

    @Test
    void acceptsPastedFullEndpointWithoutDuplicatingPath() {
        scripted.add(ok("hello"));

        OpenAiCompatibleAiClient client =
                new OpenAiCompatibleAiClient(base + "/v1/chat/completions", "k", "m");

        assertThat(client.complete("s", "u")).isEqualTo("hello");
        assertThat(paths).containsExactly("/v1/chat/completions");
    }

    @Test
    void detectsVersionSuffix() {
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://api.openai.com/v1")).isTrue();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://api.deepseek.com/v1/")).isTrue();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://ark.cn-beijing.volces.com/api/v3")).isTrue();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://open.bigmodel.cn/api/paas/v4")).isTrue();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://generativelanguage.googleapis.com/v1beta"))
                .isTrue();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("https://api.openai.com")).isFalse();
        assertThat(OpenAiCompatibleAiClient.hasVersionSuffix("http://127.0.0.1:9999")).isFalse();
    }

    @Test
    void notFoundPointsAtEndpointAndMissingVersionSegment() {
        scripted.add(new Stub(404, "{\"error\":{\"message\":\"Not Found\"}}"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base, "k", "m");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOfSatisfying(OpenAiCompatibleAiClient.ProviderHttpException.class, e -> {
                    assertThat(e.status()).isEqualTo(404);
                    assertThat(e.getMessage())
                            .contains("POST " + base + "/chat/completions")
                            .contains("/v1");
                });
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> new OpenAiCompatibleAiClient("   ", "k", "m"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------- response diagnostics

    @Test
    void emptyContentReportsFinishReason() {
        scripted.add(new Stub(200,
                "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "k", "o3-mini");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .hasMessageContaining("empty content")
                .hasMessageContaining("finish_reason=length");
    }

    @Test
    void noChoicesIsReported() {
        scripted.add(new Stub(200, "{\"choices\":[]}"));

        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(base + "/v1", "k", "m");

        assertThatThrownBy(() -> client.complete("s", "u")).hasMessageContaining("no choices");
    }

    // ---------------------------------------------------------- testConnection

    @Test
    void testConnectionReturnsProviderMessageNotWrapperNoise() {
        scripted.add(new Stub(401, "{\"error\":{\"message\":\"invalid api key\"}}"));

        String reason = AiSettingsService.testConnection(base + "/v1", "bad", "m");

        assertThat(reason).contains("invalid api key").doesNotContain("I/O error");
    }

    @Test
    void testConnectionSucceedsAgainstStub() {
        scripted.add(ok("ok"));

        assertThat(AiSettingsService.testConnection(base + "/v1", "k", "m")).isNull();
    }

    @Test
    void connectionRefusedIsExplainedInPlainLanguage() {
        String reason = AiSettingsService.testConnection("http://127.0.0.1:1/v1", "k", "m");

        assertThat(reason)
                .contains("无法连接")
                .contains("http://127.0.0.1:1/v1/chat/completions")
                .contains("连接被拒绝")
                .doesNotContain(": null");
    }

    @Test
    void transportFailureNamesTheEndpointAndNeverSaysNull() {
        // .invalid is a reserved TLD — resolution must fail. Some corporate DNS
        // resolvers hijack it, so only assert on the shape of the message.
        String reason = AiSettingsService.testConnection("http://no-such-host.invalid/v1", "k", "m");

        assertThat(reason)
                .contains("无法连接 POST http://no-such-host.invalid/v1/chat/completions")
                .doesNotContain(": null");
    }

    // --------------------------------------------------------------- helpers

    private static Stub ok(String content) {
        return new Stub(200, "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}");
    }
}

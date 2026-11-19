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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-level proof that the main AI path really runs on Spring AI's
 * {@code OpenAiChatModel}: request shape, Spring AI's own response mapping,
 * reasoning-model fallback and keyless providers.
 */
class SpringAiClientTest {

    private record Stub(int status, String body) {}

    private HttpServer server;
    private String base;
    private final Queue<Stub> scripted = new ConcurrentLinkedQueue<>();
    private final Queue<String> bodies = new ConcurrentLinkedQueue<>();
    private final Queue<String> authHeaders = new ConcurrentLinkedQueue<>();
    private final Queue<String> paths = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            authHeaders.add(auth == null ? "<none>" : auth);
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

    @Test
    void goesThroughSpringAiWithExpectedRequestShape() {
        scripted.add(ok("hello from Spring AI"));

        SpringAiClient client = new SpringAiClient(base + "/v1", "sk-test", "deepseek-chat");

        assertThat(client.complete("be brief", "hi")).isEqualTo("hello from Spring AI");
        assertThat(paths).containsExactly("/v1/chat/completions");
        assertThat(bodies.element())
                .contains("\"model\":\"deepseek-chat\"")
                .contains("be brief")
                .contains("\"temperature\":0.2");
        assertThat(client.describe()).contains("SpringAI[deepseek-chat@");
    }

    @Test
    void keylessProviderSendsNoAuthorizationHeader() {
        scripted.add(ok("ok"));

        new SpringAiClient(base + "/v1", "  ", "llama3").complete("s", "u");

        assertThat(authHeaders.element()).isEqualTo("<none>");
    }

    @Test
    void fallsBackWithoutTemperatureForReasoningModels() {
        scripted.add(new Stub(400, """
                {"error":{"message":"Unsupported value: 'temperature' is not supported with this model.",\
                "param":"temperature"}}"""));
        scripted.add(ok("ok"));

        SpringAiClient client = new SpringAiClient(base + "/v1", "k", "gpt-5");

        assertThat(client.complete("s", "u")).isEqualTo("ok");
        assertThat(bodies).hasSize(2);
        assertThat(bodies.toArray()[0].toString()).contains("temperature");
        assertThat(bodies.toArray()[1].toString()).doesNotContain("temperature");
    }

    @Test
    void unrelatedBadRequestIsNotRetriedAndNamesEndpoint() {
        scripted.add(new Stub(401, "{\"error\":{\"message\":\"invalid api key\"}}"));

        SpringAiClient client = new SpringAiClient(base + "/v1", "bad", "m");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("POST " + base + "/v1/chat/completions")
                .hasMessageContaining("invalid api key");
        assertThat(bodies).hasSize(1);
    }

    @Test
    void normalizesPastedEndpoint() {
        scripted.add(ok("ok"));

        SpringAiClient client = new SpringAiClient(base + "/v1/chat/completions", "k", "m");
        assertThat(client.complete("s", "u")).isEqualTo("ok");
        assertThat(paths).containsExactly("/v1/chat/completions");
    }

    @Test
    void noChoicesIsReported() {
        scripted.add(new Stub(200, "{\"choices\":[]}"));

        SpringAiClient client = new SpringAiClient(base + "/v1", "k", "m");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .hasMessageContaining("no choices")
                .hasMessageContaining(base + "/v1/chat/completions");
    }

    private static Stub ok(String content) {
        return new Stub(200, "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}");
    }
}

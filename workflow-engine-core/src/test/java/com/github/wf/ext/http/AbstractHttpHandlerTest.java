package com.github.wf.ext.http;

import com.github.wf.ext.ServiceTaskHandler;
import org.junit.jupiter.api.Test;
import java.net.http.HttpResponse;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractHttpHandlerTest {

    @Test
    void subClassOnlyNeedsGetUrl() {
        var handler = new AbstractHttpHandler() {
            @Override
            protected String getUrl(Map<String, Object> variables) {
                return "https://httpbin.org/post";
            }
        };

        assertThat(handler.getMethod()).isEqualTo("POST");
        assertThat(handler.getTimeout().getSeconds()).isEqualTo(30);
        assertThat(handler.getHeaders(Map.of())).containsKey("Content-Type");
    }

    @Test
    void canOverrideAllSettings() {
        var handler = new AbstractHttpHandler() {
            @Override
            protected String getUrl(Map<String, Object> variables) {
                return "https://api.example.com/users/" + variables.get("userId");
            }
            @Override
            protected String getMethod() { return "GET"; }
            @Override
            protected Map<String, String> getHeaders(Map<String, Object> v) {
                return Map.of("Authorization", "Bearer " + v.get("token"));
            }
            @Override
            protected java.time.Duration getTimeout() { return java.time.Duration.ofSeconds(5); }
        };

        assertThat(handler.getMethod()).isEqualTo("GET");
        assertThat(handler.getTimeout().getSeconds()).isEqualTo(5);
        assertThat(handler.getHeaders(Map.of("token", "abc123")))
                .containsEntry("Authorization", "Bearer abc123");
    }
}

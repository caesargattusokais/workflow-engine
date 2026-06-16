package com.github.wf.ext.http;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpStatusExceptionTest {

    @Test
    void storesStatusCodeAndBody() {
        HttpStatusException ex = new HttpStatusException(503, "Service Unavailable");
        assertThat(ex.getStatusCode()).isEqualTo(503);
        assertThat(ex.getResponseBody()).isEqualTo("Service Unavailable");
        assertThat(ex.getMessage()).contains("503").contains("Service Unavailable");
    }

    @Test
    void handlesNullBody() {
        HttpStatusException ex = new HttpStatusException(500, null);
        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getResponseBody()).isNull();
        assertThat(ex.getMessage()).contains("500");
    }
}

package com.github.wf.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wf.auth.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** Focused tests for the auth filter wiring, independent of the full Spring context. */
class AuthIntegrationTest {

    private AuthProperties props;
    private AuthService service;
    private AuthenticationFilter filter;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        props = new AuthProperties();
        props.setSecret("integration-test-secret-0123456789abcdef");
        props.setTokenTtl("8h");
        props.setCookieName("wf_session");
        props.setHeaderCompatEnabled(true);
        AuthProperties.User u = new AuthProperties.User();
        u.setUsername("admin");
        u.setPassword("pass1234");
        u.setRoles(List.of("ADMIN"));
        props.setUsers(List.of(u));

        UserStore store = new InMemoryUserStore(props);
        TokenService tokens = new TokenService(props);
        service = new AuthService(store, tokens);
        filter = new AuthenticationFilter(service, props);
    }

    @Test
    void validTokenInjectsUserIdHeader() throws Exception {
        String token = service.issueToken(service.authenticate("admin", "pass1234"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/definitions");
        req.setCookies(new jakarta.servlet.http.Cookie("wf_session", token));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(req.getAttribute(CurrentUser.ATTRIBUTE)).isNotNull();
        assertThat(((CurrentUser) req.getAttribute(CurrentUser.ATTRIBUTE)).getUser().getUsername()).isEqualTo("admin");
    }

    @Test
    void bearerTokenAlsoAccepted() throws Exception {
        String token = service.issueToken(service.authenticate("admin", "pass1234"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/definitions");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(((CurrentUser) req.getAttribute(CurrentUser.ATTRIBUTE)).getSource()).isEqualTo("token");
    }

    @Test
    void legacyHeaderStillWorks() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/definitions");
        req.addHeader("X-User-Id", "mcp-agent");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(((CurrentUser) req.getAttribute(CurrentUser.ATTRIBUTE)).getSource()).isEqualTo("header");
    }

    @Test
    void missingIdentityReturns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/definitions");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void headerCompatCanBeDisabled() throws Exception {
        props.setHeaderCompatEnabled(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/definitions");
        req.addHeader("X-User-Id", "mcp-agent");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void loginPathIsNotFiltered() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        // With an empty chain, a filtered request would hit the 401 branch;
        // a skipped request proceeds straight through with default 200.
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}

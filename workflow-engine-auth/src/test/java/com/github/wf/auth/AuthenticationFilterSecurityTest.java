package com.github.wf.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Security invariants for the identity gate. The key regression: a forged or
 * expired token combined with a spoofed X-User-Id must NOT authenticate as the
 * header user, and a bare header must be rejected unless compat is explicitly on.
 */
class AuthenticationFilterSecurityTest {

    private AuthProperties props() {
        AuthProperties p = new AuthProperties();
        p.setSecret("test-secret-for-hmac-signing-0123456789");
        p.setTokenTtl("8h");
        AuthProperties.User u = new AuthProperties.User();
        u.setUsername("admin");
        u.setPassword("s3cret!");
        u.setRoles(List.of("ADMIN"));
        p.setUsers(List.of(u));
        return p;
    }

    private record Result(int status, String headerUser, CurrentUser current, boolean chainCalled,
                          String downstreamHeader) {}

    private Result run(AuthProperties p, String token, String headerUser) throws ServletException, IOException {
        AuthService svc = new AuthService(new InMemoryUserStore(p), new TokenService(p));
        AuthenticationFilter filter = new AuthenticationFilter(svc, p);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/instances");
        if (token != null) {
            if (token.startsWith("cookie:")) {
                req.setCookies(new Cookie(p.getCookieName(), token.substring(7)));
            } else {
                req.addHeader("Authorization", "Bearer " + token);
            }
        }
        if (headerUser != null) req.addHeader("X-User-Id", headerUser);
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);
        String[] downstream = new String[1];
        FilterChain chain = (r, s) -> {
            called.set(true);
            downstream[0] = ((jakarta.servlet.http.HttpServletRequest) r).getHeader("X-User-Id");
        };
        filter.doFilter(req, res, chain);
        return new Result(res.getStatus(), req.getHeader("X-User-Id"),
                (CurrentUser) req.getAttribute(CurrentUser.ATTRIBUTE), called.get(), downstream[0]);
    }

    @Test
    void headerCompatDisabledByDefault() throws Exception {
        AuthProperties p = props();
        assertThat(p.isHeaderCompatEnabled()).isFalse();
        Result r = run(p, null, "admin");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.chainCalled()).isFalse();
    }

    @Test
    void forgedTokenWithAdminHeaderIsRejected() throws Exception {
        AuthProperties p = props();
        p.setHeaderCompatEnabled(true); // even WITH compat on ...
        Result r = run(p, "forged.token.value", "admin");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.chainCalled()).isFalse();
        assertThat(r.current()).isNull();
    }

    @Test
    void expiredTokenWithAdminHeaderIsRejected() throws Exception {
        AuthProperties p = props();
        p.setHeaderCompatEnabled(true);
        p.setTokenTtl("1ms");
        String token = new TokenService(p).issue("admin");
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        Result r = run(p, token, "admin");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.chainCalled()).isFalse();
    }

    @Test
    void headerWorksWhenExplicitlyEnabled() throws Exception {
        AuthProperties p = props();
        p.setHeaderCompatEnabled(true);
        Result r = run(p, null, "admin");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.chainCalled()).isTrue();
        assertThat(r.current()).isNotNull();
    }

    @Test
    void validCookieTokenAuthenticatesAndRewritesHeader() throws Exception {
        AuthProperties p = props();
        String token = new TokenService(p).issue("admin");
        Result r = run(p, "cookie:" + token, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.chainCalled()).isTrue();
        assertThat(r.current().getUser().getUsername()).isEqualTo("admin");
        assertThat(r.downstreamHeader()).isEqualTo("admin");
    }
}

package com.github.wf.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AuthModuleTest {

    private AuthProperties props(String ttl) {
        AuthProperties p = new AuthProperties();
        p.setSecret("test-secret-for-hmac-signing-0123456789");
        p.setTokenTtl(ttl);
        AuthProperties.User u = new AuthProperties.User();
        u.setUsername("admin");
        u.setPassword("s3cret!");
        u.setDisplayName("Administrator");
        u.setRoles(List.of("ADMIN"));
        p.setUsers(List.of(u));
        return p;
    }

    @Test
    void plainPasswordIsHashedAndVerified() {
        AuthService svc = new AuthService(new InMemoryUserStore(props("8h")), new TokenService(props("8h")));
        AuthUser user = svc.authenticate("admin", "s3cret!");
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getDisplayName()).isEqualTo("Administrator");
        assertThatThrownBy(() -> svc.authenticate("admin", "wrong"))
                .isInstanceOf(AuthService.BadCredentialsException.class);
        assertThatThrownBy(() -> svc.authenticate("nobody", "s3cret!"))
                .isInstanceOf(AuthService.BadCredentialsException.class);
    }

    @Test
    void tokenRoundTripCarriesUsername() {
        TokenService tokens = new TokenService(props("8h"));
        String token = tokens.issue("admin");
        TokenService.Claims c = tokens.verify(token);
        assertThat(c.username()).isEqualTo("admin");
        assertThat(c.jti()).isNotBlank();
    }

    @Test
    void tamperedTokenIsRejected() {
        TokenService tokens = new TokenService(props("8h"));
        String token = tokens.issue("admin");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> tokens.verify(tampered))
                .isInstanceOf(TokenService.InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        TokenService tokens = new TokenService(props("1ms"));
        String token = tokens.issue("admin");
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOf(TokenService.InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void logoutRevokesImmediately() {
        TokenService tokens = new TokenService(props("8h"));
        String token = tokens.issue("admin");
        assertThat(tokens.verify(token).username()).isEqualTo("admin");
        tokens.revokeToken(token);
        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOf(TokenService.InvalidTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void durationParsing() {
        assertThat(TokenService.parseDuration("30m")).isEqualTo(30L * 60_000);
        assertThat(TokenService.parseDuration("8h")).isEqualTo(8L * 3600_000);
        assertThat(TokenService.parseDuration("2d")).isEqualTo(2L * 86_400_000);
        assertThat(TokenService.parseDuration("bad")).isEqualTo(8L * 3600_000);
    }
}

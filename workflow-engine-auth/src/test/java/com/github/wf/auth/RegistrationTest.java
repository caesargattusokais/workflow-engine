package com.github.wf.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RegistrationTest {
    private AuthProperties props() {
        AuthProperties p = new AuthProperties();
        p.setSecret("test-secret-for-hmac-signing-0123456789");
        p.setTokenTtl("8h");
        p.setMinPasswordLength(6);
        p.setDefaultRoles(List.of("USER"));
        return p;
    }

    @Test
    void registerCreatesUserWhoCanImmediatelyLogIn() {
        AuthProperties p = props();
        InMemoryUserStore store = new InMemoryUserStore(p);
        AuthService svc = new AuthService(store, new TokenService(p), p);

        AuthUser created = svc.register("newuser", "pass1234", "New User", "n@example.com");
        assertThat(created.getUsername()).isEqualTo("newuser");
        assertThat(created.getRoles()).containsExactly("USER");
        assertThat(svc.authenticate("newuser", "pass1234").getUsername()).isEqualTo("newuser");
        assertThatThrownBy(() -> svc.authenticate("newuser", "wrongpass"))
                .isInstanceOf(AuthService.BadCredentialsException.class);
    }

    @Test
    void duplicateUsernameRejected() {
        AuthProperties p = props();
        AuthService svc = new AuthService(new InMemoryUserStore(p), new TokenService(p), p);
        svc.register("alice", "pass1234", null, null);
        assertThatThrownBy(() -> svc.register("alice", "other123", null, null))
                .isInstanceOf(AuthService.DuplicateUsernameException.class);
        // case-insensitive duplicate
        assertThatThrownBy(() -> svc.register("ALICE", "other123", null, null))
                .isInstanceOf(AuthService.DuplicateUsernameException.class);
    }

    @Test
    void weakPasswordAndBadUsernameRejected() {
        AuthProperties p = props();
        AuthService svc = new AuthService(new InMemoryUserStore(p), new TokenService(p), p);
        assertThatThrownBy(() -> svc.register("bob", "123", null, null))
                .isInstanceOf(AuthService.InvalidRegistrationException.class);
        assertThatThrownBy(() -> svc.register("ab", "pass1234", null, null))
                .isInstanceOf(AuthService.InvalidRegistrationException.class);
        assertThatThrownBy(() -> svc.register("bad name!", "pass1234", null, null))
                .isInstanceOf(AuthService.InvalidRegistrationException.class);
    }

    @Test
    void disabledRegistrationRejectsAll() {
        AuthProperties p = props();
        p.setRegistrationEnabled(false);
        AuthService svc = new AuthService(new InMemoryUserStore(p), new TokenService(p), p);
        assertThatThrownBy(() -> svc.register("bob", "pass1234", null, null))
                .isInstanceOf(AuthService.RegistrationDisabledException.class);
    }
}


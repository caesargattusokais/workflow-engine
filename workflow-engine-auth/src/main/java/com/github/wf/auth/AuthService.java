package com.github.wf.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

/** Authentication business logic: verify credentials and mint/revoke tokens. */
public class AuthService {

    private final UserStore store;
    private final TokenService tokens;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserStore store, TokenService tokens) {
        this.store = store;
        this.tokens = tokens;
    }

    /** Authenticate credentials. Throws {@link BadCredentialsException} on failure. */
    public AuthUser authenticate(String username, String password) {
        if (username == null || password == null) throw new BadCredentialsException();
        Optional<UserStore.StoredUser> found = store.findByUsername(username.trim());
        if (found.isEmpty()) throw new BadCredentialsException();
        UserStore.StoredUser su = found.get();
        if (!su.user().isEnabled()) throw new BadCredentialsException();
        if (!encoder.matches(password, su.passwordHash())) throw new BadCredentialsException();
        return su.user();
    }

    public String issueToken(AuthUser user) { return tokens.issue(user.getUsername()); }
    public TokenService.Claims verifyToken(String token) { return tokens.verify(token); }
    public void logout(String token) { tokens.revokeToken(token); }
    public TokenService getTokenService() { return tokens; }

    /** Look up a stored user by login name (used by the filter to hydrate the principal). */
    public UserStore.StoredUser findUser(String username) {
        return store.findByUsername(username).orElse(null);
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException() { super("Invalid username or password"); }
    }
}

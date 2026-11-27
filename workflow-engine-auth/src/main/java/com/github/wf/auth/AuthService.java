package com.github.wf.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

/** Authentication business logic: verify credentials and mint/revoke tokens. */
public class AuthService {

    private final UserStore store;
    private final TokenService tokens;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthProperties props;

    public AuthService(UserStore store, TokenService tokens) {
        this(store, tokens, new AuthProperties());
    }

    public AuthService(UserStore store, TokenService tokens, AuthProperties props) {
        this.store = store;
        this.tokens = tokens;
        this.props = props;
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

    /** Register a new account. Throws on invalid input or a duplicate username. */
    public AuthUser register(String username, String password, String displayName, String email) {
        if (!props.isRegistrationEnabled()) throw new RegistrationDisabledException();
        if (username == null || username.isBlank()) throw new InvalidRegistrationException("用户名不能为空");
        String uname = username.trim();
        if (!uname.matches("[A-Za-z0-9_.\\-]{3,32}"))
            throw new InvalidRegistrationException("用户名需 3-32 位，仅限字母、数字、点、下划线、连字符");
        int min = props.getMinPasswordLength();
        if (password == null || password.length() < min)
            throw new InvalidRegistrationException("密码至少 " + min + " 位");
        if (store.findByUsername(uname).isPresent()) throw new DuplicateUsernameException();

        AuthUser user = new AuthUser(uname,
                displayName == null || displayName.isBlank() ? uname : displayName.trim(),
                email == null || email.isBlank() ? null : email.trim(),
                props.getDefaultRoles(), List.of(), List.of(), true);
        if (!store.create(user, password)) throw new DuplicateUsernameException();
        return user;
    }

    public String issueToken(AuthUser user) { return tokens.issue(user.getUsername()); }
    public TokenService.Claims verifyToken(String token) { return tokens.verify(token); }
    public void logout(String token) { tokens.revokeToken(token); }
    public TokenService getTokenService() { return tokens; }
    public AuthProperties getProps() { return props; }

    /** Look up a stored user by login name (used by the filter to hydrate the principal). */
    public UserStore.StoredUser findUser(String username) {
        return store.findByUsername(username).orElse(null);
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException() { super("Invalid username or password"); }
    }
    public static class InvalidRegistrationException extends RuntimeException {
        public InvalidRegistrationException(String message) { super(message); }
    }
    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException() { super("Username already exists"); }
    }
    public static class RegistrationDisabledException extends RuntimeException {
        public RegistrationDisabledException() { super("Registration is disabled"); }
    }
}

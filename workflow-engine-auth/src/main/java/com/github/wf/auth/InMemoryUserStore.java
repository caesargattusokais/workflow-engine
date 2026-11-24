package com.github.wf.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Seeds users from {@link AuthProperties} at startup. Plain passwords are hashed once and discarded. */
public class InMemoryUserStore implements UserStore {

    private final Map<String, StoredUser> users = new LinkedHashMap<>();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public InMemoryUserStore(AuthProperties props) {
        for (AuthProperties.User u : props.getUsers()) {
            if (u.getUsername() == null || u.getUsername().isBlank()) continue;
            String hash = (u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                    ? u.getPasswordHash()
                    : (u.getPassword() != null ? encoder.encode(u.getPassword()) : null);
            if (hash == null) continue;
            AuthUser principal = new AuthUser(
                    u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(), u.isEnabled());
            users.put(u.getUsername().toLowerCase(), new StoredUser(principal, hash));
        }
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(users.get(username.toLowerCase()));
    }

    /** Exposed for tests/bootstrap to seed additional users at runtime. */
    public void put(AuthUser user, String rawPassword) {
        users.put(user.getUsername().toLowerCase(), new StoredUser(user, encoder.encode(rawPassword)));
    }
}

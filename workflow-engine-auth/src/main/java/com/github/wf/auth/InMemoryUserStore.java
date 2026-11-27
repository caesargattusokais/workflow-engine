package com.github.wf.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                    u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(),
                    u.getGroups(), u.getAdminGroups(), u.isEnabled());
            users.put(u.getUsername().toLowerCase(), new StoredUser(principal, hash));
        }
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(users.get(username.toLowerCase()));
    }

    /** Exposed for tests/bootstrap to seed additional users at runtime (overwrites). */
    public void put(AuthUser user, String rawPassword) {
        users.put(user.getUsername().toLowerCase(), new StoredUser(user, encoder.encode(rawPassword)));
    }

    /** Create a new user, rejecting duplicates. */
    @Override
    public boolean create(AuthUser user, String rawPassword) {
        String key = user.getUsername().toLowerCase();
        if (users.containsKey(key)) return false;
        users.put(key, new StoredUser(user, encoder.encode(rawPassword)));
        return true;
    }

    @Override
    public List<AuthUser> listAll() {
        return new ArrayList<>(users.values()).stream().map(StoredUser::user).toList();
    }

    @Override
    public boolean updateMembership(String username, List<String> groups, List<String> adminGroups) {
        if (username == null) return false;
        String key = username.toLowerCase(Locale.ROOT);
        StoredUser existing = users.get(key);
        if (existing == null) return false;
        AuthUser old = existing.user();
        AuthUser updated = new AuthUser(old.getUsername(), old.getDisplayName(), old.getEmail(),
                old.getRoles(), groups == null ? List.of() : groups,
                adminGroups == null ? List.of() : adminGroups, old.isEnabled());
        users.put(key, new StoredUser(updated, existing.passwordHash()));
        return true;
    }
}

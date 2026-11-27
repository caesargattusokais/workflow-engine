package com.github.wf.auth;

/** Per-request authenticated principal, stored as a request attribute by AuthenticationFilter. */
public final class CurrentUser {
    public static final String ATTRIBUTE = "wf.auth.user";

    private final AuthUser user;
    /** "token" | "header" | "none" — how identity was established. */
    private final String source;

    public CurrentUser(AuthUser user, String source) {
        this.user = user;
        this.source = source;
    }

    public AuthUser getUser() { return user; }
    public String getSource() { return source; }

    /** A synthetic principal for the legacy X-User-Id compatibility path. */
    public static CurrentUser fromHeader(String userId) {
        return fromHeader(userId, java.util.List.of(), java.util.List.of());
    }

    public static CurrentUser fromHeader(String userId, java.util.List<String> groups,
                                         java.util.List<String> adminGroups) {
        return new CurrentUser(new AuthUser(userId, userId, null, java.util.List.of(),
                groups, adminGroups, true), "header");
    }
}

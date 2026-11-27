package com.github.wf.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * Rewrites identity headers to the authenticated principal so downstream
 * controllers keep using {@code @RequestHeader(...)} unchanged.
 *
 * <p>X-User-Id / X-Group-Ids / X-Admin-Group-Ids are server-authoritative —
 * client-supplied values are discarded. X-Group-Id (the "current group" for
 * creating new data) is client-selected but validated against membership:
 * an unknown group is cleared, which degrades safely to private data.
 */
public class AuthenticatedRequestWrapper extends HttpServletRequestWrapper {
    private final String userId;
    private final String groupIdsCsv;
    private final String adminGroupIdsCsv;
    private final String currentGroupId;

    public AuthenticatedRequestWrapper(HttpServletRequest request, AuthUser user) {
        this(request, user.getUsername(), user.getGroups(), user.getAdminGroups());
    }

    /** Legacy constructor: no group membership (data stays private). */
    public AuthenticatedRequestWrapper(HttpServletRequest request, String userId) {
        this(request, userId, List.of(), List.of());
    }

    public AuthenticatedRequestWrapper(HttpServletRequest request, String userId,
                                       List<String> groupIds, List<String> adminGroupIds) {
        super(request);
        this.userId = userId;
        this.groupIdsCsv = String.join(",", groupIds == null ? List.of() : groupIds);
        this.adminGroupIdsCsv = String.join(",", adminGroupIds == null ? List.of() : adminGroupIds);
        String requested = request.getHeader("X-Group-Id");
        boolean member = requested != null && !requested.isBlank()
                && groupIds != null && groupIds.contains(requested.trim());
        this.currentGroupId = member ? requested.trim() : "";
    }

    private String identityHeader(String name) {
        if ("X-User-Id".equalsIgnoreCase(name)) return userId;
        if ("X-Group-Ids".equalsIgnoreCase(name)) return groupIdsCsv;
        if ("X-Admin-Group-Ids".equalsIgnoreCase(name)) return adminGroupIdsCsv;
        if ("X-Group-Id".equalsIgnoreCase(name)) return currentGroupId;
        return null;
    }

    @Override
    public String getHeader(String name) {
        String identity = identityHeader(name);
        return identity != null ? identity : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String identity = identityHeader(name);
        if (identity != null) {
            return Collections.enumeration(new Vector<>(List.of(identity)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Vector<String> names = new Vector<>(Collections.list(super.getHeaderNames()));
        for (String h : new String[]{"X-User-Id", "X-Group-Ids", "X-Admin-Group-Ids", "X-Group-Id"}) {
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase(h))) names.add(h);
        }
        return names.elements();
    }
}

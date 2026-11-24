package com.github.wf.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Rewrites the X-User-Id header to the authenticated identity so downstream
 * controllers keep using {@code @RequestHeader("X-User-Id")} unchanged.
 * This is the seam that lets auth land without touching every controller.
 */
public class AuthenticatedRequestWrapper extends HttpServletRequestWrapper {
    private final String userId;

    public AuthenticatedRequestWrapper(HttpServletRequest request, String userId) {
        super(request);
        this.userId = userId;
    }

    @Override
    public String getHeader(String name) {
        if ("X-User-Id".equalsIgnoreCase(name)) return userId;
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if ("X-User-Id".equalsIgnoreCase(name)) {
            return Collections.enumeration(new Vector<>(java.util.List.of(userId)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Vector<String> names = new Vector<>(Collections.list(super.getHeaderNames()));
        if (!names.contains("X-User-Id") && !names.stream().anyMatch(n -> n.equalsIgnoreCase("X-User-Id"))) {
            names.add("X-User-Id");
        }
        return names.elements();
    }
}

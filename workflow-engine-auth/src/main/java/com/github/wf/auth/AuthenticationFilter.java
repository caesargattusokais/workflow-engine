package com.github.wf.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Resolves identity for every {@code /api/**} call in priority order:
 *   1. signed session token (cookie, then Authorization: Bearer)
 *   2. legacy X-User-Id header (when {@code auth.header-compat-enabled=true})
 *
 * <p>The resolved identity is exposed as a {@link CurrentUser} request attribute
 * AND written back into the X-User-Id header, so existing controllers and the
 * tenant guard keep working with no changes.
 */
public class AuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final AuthProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthenticationFilter(AuthService authService, AuthProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;
        String path = request.getRequestURI();
        // Non-API paths and public auth endpoints (login + health probe) are open.
        return !path.startsWith("/api/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            // A presented token is authoritative: a forged/expired token must NOT
            // fall back to the spoofable header. This closes the "bad token +
            // X-User-Id: admin" privilege-escalation path.
            try {
                TokenService.Claims claims = authService.verifyToken(token);
                UserStore.StoredUser stored = findUser(claims.username());
                if (stored != null && stored.user().isEnabled()) {
                    CurrentUser cu = new CurrentUser(stored.user(), "token");
                    request.setAttribute(CurrentUser.ATTRIBUTE, cu);
                    chain.doFilter(new AuthenticatedRequestWrapper(request, stored.user()), response);
                    return;
                }
            } catch (TokenService.InvalidTokenException ignored) {
                // fall through to 401 — never to header compat
            }
            respondUnauthorized(response);
            return;
        }

        String headerUser = request.getHeader("X-User-Id");
        if (props.isHeaderCompatEnabled() && headerUser != null && !headerUser.isBlank()) {
            String uid = headerUser.trim();
            // In explicit compat mode service callers may declare their own
            // group scope; no token is present to make it authoritative.
            request.setAttribute(CurrentUser.ATTRIBUTE, CurrentUser.fromHeader(uid,
                    csv(request.getHeader("X-Group-Ids")), csv(request.getHeader("X-Admin-Group-Ids"))));
            chain.doFilter(request, response);
            return;
        }

        respondUnauthorized(response);
    }

    private static java.util.List<String> csv(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : value.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return java.util.List.copyOf(out);
    }

    private void respondUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of(
                "error", "Unauthorized",
                "message", "Login required or session expired / 请先登录或重新登录"));
    }

    /** Cookie first (browser default), then Authorization: Bearer (service callers). */
    String extractToken(HttpServletRequest request) {
        if (props.getCookieName() != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (props.getCookieName().equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                        return c.getValue();
                    }
                }
            }
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String t = auth.substring(7).trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private UserStore.StoredUser findUser(String username) {
        // Access the store through the service's public accessor.
        return authService.findUser(username);
    }
}

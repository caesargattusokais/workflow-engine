package com.github.wf.server.ws;

import com.github.wf.auth.AuthProperties;
import com.github.wf.auth.AuthService;
import com.github.wf.auth.TokenService;
import com.github.wf.auth.UserStore;
import com.github.wf.engine.WorkflowEngine;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates WebSocket handshakes — the servlet {@code AuthenticationFilter}
 * only covers /api/**, so without this any client could subscribe to any
 * instance and receive its variables/tasks/history.
 *
 * <p>Browser clients send the session cookie on the WS handshake automatically;
 * service callers may use {@code Authorization: Bearer}. When header compat is
 * explicitly enabled (E2E only), {@code X-User-Id} is accepted as a fallback.
 */
@Component
public class WsAuthInterceptor implements HandshakeInterceptor {

    public static final String WS_USER = "ws.userId";
    private static final Logger log = LoggerFactory.getLogger(WsAuthInterceptor.class);

    private final AuthService authService;
    private final AuthProperties props;
    private final WorkflowEngine engine;

    public WsAuthInterceptor(AuthService authService, AuthProperties props, WorkflowEngine engine) {
        this.authService = authService;
        this.props = props;
        this.engine = engine;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!props.isEnabled()) return true;

        HttpServletRequest servlet = request instanceof ServletServerHttpRequest sr
                ? sr.getServletRequest() : null;
        if (servlet == null) return false;

        String userId = resolveUser(servlet);
        if (userId == null) {
            log.warn("WS handshake rejected: no valid identity from {}", servlet.getRemoteAddr());
            return false;
        }

        // Per-instance channel: verify the instance belongs to this user.
        String path = servlet.getRequestURI();
        if (path != null && path.startsWith("/ws/instance/")) {
            String instanceId = path.substring("/ws/instance/".length()).split("/")[0];
            var inst = engine.instanceRepository.findById(instanceId);
            if (inst == null || !userId.equals(inst.getVariable("_userId"))) {
                log.warn("WS handshake rejected: instance {} not owned by {}", instanceId, userId);
                return false;
            }
        }

        attributes.put(WS_USER, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String resolveUser(HttpServletRequest req) {
        // 1) signed token (cookie first, then Bearer)
        String token = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (props.getCookieName().equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    token = c.getValue();
                    break;
                }
            }
        }
        if (token == null) {
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7).trim();
        }
        if (token != null) {
            try {
                TokenService.Claims claims = authService.verifyToken(token);
                UserStore.StoredUser stored = authService.findUser(claims.username());
                if (stored != null && stored.user().isEnabled()) return stored.user().getUsername();
            } catch (TokenService.InvalidTokenException ignored) {
                // A presented token is authoritative — do not fall back to header.
                return null;
            }
        }
        // 2) explicit legacy compat (E2E service callers only)
        if (props.isHeaderCompatEnabled()) {
            String h = req.getHeader("X-User-Id");
            if (h != null && !h.isBlank()) return h.trim();
        }
        return null;
    }
}

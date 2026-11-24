package com.github.wf.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public authentication endpoints. Mounted at /api/auth by the server. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties props;

    public AuthController(AuthService authService, AuthProperties props) {
        this.authService = authService;
        this.props = props;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, AuthUser.View user, long expiresInSeconds) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    @PostMapping("/login")
    public org.springframework.http.ResponseEntity<?> login(@RequestBody LoginRequest req,
                                                            HttpServletResponse response) {
        try {
            AuthUser user = authService.authenticate(req.username(), req.password());
            String token = authService.issueToken(user);
            response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, tokenMaxAgeSeconds()).toString());
            return org.springframework.http.ResponseEntity.ok(new LoginResponse(
                    token, AuthUser.View.of(user), tokenMaxAgeSeconds()));
        } catch (AuthService.BadCredentialsException e) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("error", "Bad credentials", "message", "用户名或密码错误"));
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request, props.getCookieName());
        if (token == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7).trim();
        }
        authService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
        return Map.of("ok", true);
    }

    /** Returns the current identity. Useful for the frontend to bootstrap session state. */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        CurrentUser cu = (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
        if (cu == null || cu.getUser() == null) {
            return Map.of("authenticated", false);
        }
        return Map.of(
                "authenticated", true,
                "user", AuthUser.View.of(cu.getUser()),
                "source", cu.getSource()
        );
    }

    /** Health/probe: public, no credentials required. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", props.isEnabled(), "headerCompat", props.isHeaderCompatEnabled());
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(props.getCookieName(), value)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds);
        return b.build();
    }

    private long tokenMaxAgeSeconds() {
        return authService.getTokenService().getTtlMillis() / 1000;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || name == null) return null;
        for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue();
        return null;
    }
}

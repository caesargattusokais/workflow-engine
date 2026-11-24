package com.github.wf.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compact, self-contained signed session tokens (JWT-shaped, HMAC-SHA256).
 *
 * <p>Token = base64url(header).base64url(payload).base64url(signature).
 * No external JWT dependency: the format is tiny and the signature is verified
 * with the JDK's own {@link Mac}. Revocation is tracked server-side by token id
 * so logout takes effect immediately even before expiry.
 */
public class TokenService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64DEC = Base64.getUrlDecoder();
    private static final String ALG = "HmacSHA256";

    private final byte[] secret;
    private final long ttlMillis;
    private final ObjectMapper mapper = new ObjectMapper();
    /** jti -> expiry epoch millis; entries are pruned lazily on issue/verify. */
    private final ConcurrentHashMap<String, Long> revoked = new ConcurrentHashMap<>();

    public TokenService(AuthProperties props) {
        this.secret = props.getSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = parseDuration(props.getTokenTtl());
    }

    /** Parse simple durations: 30m, 8h, 7d, 90s or a plain millisecond number. */
    static long parseDuration(String value) {
        if (value == null || value.isBlank()) return Duration.ofHours(8).toMillis();
        String s = value.trim();
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
            if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            if (s.endsWith("d")) return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Duration.ofHours(8).toMillis();
        }
    }

    /** Generate a signed token for the username. */
    public String issue(String username) {
        pruneRevoked();
        long now = System.currentTimeMillis();
        String jti = java.util.UUID.randomUUID().toString();
        String header = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        try {
            String payload = B64.encodeToString(mapper.writeValueAsBytes(Map.of(
                    "sub", username,
                    "jti", jti,
                    "iat", now / 1000,
                    "exp", (now + ttlMillis) / 1000
            )));
            String signingInput = header + "." + payload;
            return signingInput + "." + B64.encodeToString(hmac(signingInput));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue token", e);
        }
    }

    /** Result of a successful verification. */
    public record Claims(String username, String jti, long expiresAtMillis) {}

    /**
     * Verify signature + expiry + revocation. Throws {@link InvalidTokenException} on any failure.
     */
    public Claims verify(String token) {
        if (token == null || token.isBlank()) throw new InvalidTokenException("empty token");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new InvalidTokenException("malformed token");
        try {
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = B64DEC.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) throw new InvalidTokenException("bad signature");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(B64DEC.decode(parts[1]), Map.class);
            String sub = (String) payload.get("sub");
            String jti = (String) payload.get("jti");
            Object expObj = payload.get("exp");
            if (sub == null || jti == null || expObj == null) throw new InvalidTokenException("missing claims");
            long expMillis = ((Number) expObj).longValue() * 1000;
            if (expMillis < System.currentTimeMillis()) throw new InvalidTokenException("expired");
            if (revoked.containsKey(jti)) throw new InvalidTokenException("revoked");
            pruneRevoked();
            return new Claims(sub, jti, expMillis);
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("unreadable token");
        }
    }

    /** Revoke a token by jti. Subsequent verify() calls fail immediately. */
    public void revoke(String jti) {
        if (jti != null) revoked.put(jti, System.currentTimeMillis() + ttlMillis);
    }

    /** Revoke a token directly from its compact form. Bad tokens are ignored. */
    public void revokeToken(String token) {
        try { revoke(verify(token).jti()); } catch (InvalidTokenException ignored) {}
    }

    public long getTtlMillis() { return ttlMillis; }

    private void pruneRevoked() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() < now);
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret, ALG));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) { super(message); }
    }
}

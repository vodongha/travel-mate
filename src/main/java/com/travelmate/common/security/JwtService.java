package com.travelmate.common.security;

import com.travelmate.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies short-lived JWT access tokens (HS256). Refresh tokens are NOT JWTs — they
 * are opaque random strings stored hashed in {@code AUTH_TOKENS} so they can be revoked/rotated
 * (SPEC §5.1); see {@link com.travelmate.auth.AuthService}.
 *
 * <p>The signing secret must be at least 256 bits (32 bytes) for HS256.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final String issuer;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-ttl:PT60M}") Duration accessTtl,
                      @Value("${app.jwt.issuer:travel-mate}") String issuer) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = accessTtl;
        this.issuer = issuer;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getRid())
                .claim("uid", user.getId())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Parse and verify a token, returning the principal. Throws {@code JwtException} if invalid. */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(claims.get("uid", Long.class), claims.getSubject(),
                claims.get("email", String.class));
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}

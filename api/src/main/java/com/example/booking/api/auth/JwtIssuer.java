package com.example.booking.api.auth;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtIssuer {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtIssuer(@Value("${booking.jwt.secret}") String secret,
                     @Value("${booking.jwt.access-token-ttl}") Duration accessTokenTtl,
                     @Value("${booking.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String issue(Long userId, Role role) {
        return buildToken(userId, role, accessTokenTtl, "access");
    }

    public String issueRefreshToken(Long userId, Role role) {
        return buildToken(userId, role, refreshTokenTtl, "refresh");
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public AuthPrincipal verifyRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Not a refresh token");
        }
        return new AuthPrincipal(
                Long.parseLong(claims.getSubject()),
                Role.valueOf(claims.get("role", String.class))
        );
    }

    private String buildToken(Long userId, Role role, Duration ttl, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}

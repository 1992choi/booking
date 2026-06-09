package com.example.booking.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefreshTokenBlacklist {

    private static final String PREFIX = "rt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void add(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isPositive()) {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }

}

package com.bremenband.shadowengapi.global.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiryMs;

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpiryMs)
        );
    }

    public boolean validate(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        if (stored == null) {
            return false;
        }
        return MessageDigest.isEqual(
                stored.getBytes(),
                refreshToken.getBytes()
        );
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}

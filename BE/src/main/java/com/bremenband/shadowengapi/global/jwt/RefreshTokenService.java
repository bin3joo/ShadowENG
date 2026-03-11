package com.bremenband.shadowengapi.global.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_PREFIX    = "refresh_token:";
    private static final String ABS_EXP_PREFIX  = "refresh_token_abs_expiry:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiryMs;

    @Value("${jwt.max-session-expiry}")
    private long maxSessionExpiryMs;

    /**
     * 신규 로그인 또는 RTR 회전 시 호출.
     * - 신규 로그인: 절대 만료 키를 새로 설정 (maxSessionExpiry TTL)
     * - RTR 회전:   절대 만료 키의 남은 시간을 유지하고,
     *               토큰 TTL = min(slidingExpiry, 절대만료까지 남은 시간)
     */
    public void save(Long userId, String refreshToken) {
        String absKey = absExpiryKey(userId);
        Long absoluteRemainingMs = redisTemplate.getExpire(absKey, TimeUnit.MILLISECONDS);

        if (absoluteRemainingMs == null || absoluteRemainingMs < 0) {
            // 신규 세션: 절대 만료 sentinel 키 설정
            redisTemplate.opsForValue().set(absKey, "1", Duration.ofMillis(maxSessionExpiryMs));
            absoluteRemainingMs = maxSessionExpiryMs;
        }

        // Sliding TTL: 7일과 절대 만료까지 남은 시간 중 작은 값
        long ttl = Math.min(refreshTokenExpiryMs, absoluteRemainingMs);
        redisTemplate.opsForValue().set(tokenKey(userId), refreshToken, Duration.ofMillis(ttl));
    }

    /**
     * 리프레시 토큰 유효성 검증.
     * 1. 절대 세션 만료 여부 확인 (30일 초과 시 강제 로그아웃)
     * 2. 저장된 토큰과 constant-time 비교
     */
    public boolean validate(Long userId, String refreshToken) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(absExpiryKey(userId)))) {
            return false;
        }

        String stored = redisTemplate.opsForValue().get(tokenKey(userId));
        if (stored == null) {
            return false;
        }

        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                refreshToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 로그아웃 또는 RTR 재사용 감지 시 호출.
     * 토큰 키와 절대 만료 키를 모두 삭제하여 세션 완전 무효화.
     */
    public void delete(Long userId) {
        redisTemplate.delete(tokenKey(userId));
        redisTemplate.delete(absExpiryKey(userId));
    }

    private String tokenKey(Long userId) {
        return TOKEN_PREFIX + userId;
    }

    private String absExpiryKey(Long userId) {
        return ABS_EXP_PREFIX + userId;
    }
}

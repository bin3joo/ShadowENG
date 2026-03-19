package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.dto.redis.GameSessionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 진행 중인 게임 세션을 Redis에 보관.
 * key: game_session:{userId}:{level}:{date}  TTL: 24시간
 * round 1 제출 시 세션을 생성/리셋하고, 게임 종료(gameOver) 시 삭제.
 */
@Component
@RequiredArgsConstructor
public class GameSessionStore {

    private static final String PREFIX = "game_session:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(Long userId, int level, LocalDate date, GameSessionData data) {
        redisTemplate.opsForValue().set(key(userId, level, date), data, TTL);
    }

    public Optional<GameSessionData> find(Long userId, int level, LocalDate date) {
        Object raw = redisTemplate.opsForValue().get(key(userId, level, date));
        if (raw == null) return Optional.empty();
        return Optional.of(objectMapper.convertValue(raw, GameSessionData.class));
    }

    public void delete(Long userId, int level, LocalDate date) {
        redisTemplate.delete(key(userId, level, date));
    }

    private String key(Long userId, int level, LocalDate date) {
        return PREFIX + userId + ":" + level + ":" + date;
    }
}

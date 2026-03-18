package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * step 1~4 학습 사이클의 평가 결과를 Redis에 임시 보관하는 저장소.
 * step 4 완료 시 전체 사이클을 DB에 커밋한 뒤 삭제한다.
 * TTL(24시간) 초과 시 자동 만료 → 중도 이탈한 사이클 정리.
 *
 * key: pending_eval:{sessionId}:{sentenceId}:{step}
 */
@Component
@RequiredArgsConstructor
public class PendingEvaluationStore {

    private static final String   KEY_PREFIX = "pending_eval:";
    private static final Duration TTL        = Duration.ofHours(24);
    private static final int      TOTAL_STEPS = 4;

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(Long sessionId, Long sentenceId, PendingEvaluation eval) {
        redisTemplate.opsForValue().set(key(sessionId, sentenceId, eval.getStep()), eval, TTL);
    }

    public Optional<PendingEvaluation> findStep(Long sessionId, Long sentenceId, int step) {
        Object val = redisTemplate.opsForValue().get(key(sessionId, sentenceId, step));
        return Optional.ofNullable((PendingEvaluation) val);
    }

    /** step 1~4 중 Redis에 존재하는 것만 반환한다. */
    public List<PendingEvaluation> findAll(Long sessionId, Long sentenceId) {
        List<PendingEvaluation> result = new ArrayList<>();
        for (int step = 1; step <= TOTAL_STEPS; step++) {
            findStep(sessionId, sentenceId, step).ifPresent(result::add);
        }
        return result;
    }

    public void deleteAll(Long sessionId, Long sentenceId) {
        for (int step = 1; step <= TOTAL_STEPS; step++) {
            redisTemplate.delete(key(sessionId, sentenceId, step));
        }
    }

    private String key(Long sessionId, Long sentenceId, int step) {
        return KEY_PREFIX + sessionId + ":" + sentenceId + ":" + step;
    }
}

package com.bremenband.shadowengapi.domain.game.dto.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 진행 중인 게임 세션을 Redis에 임시 저장하는 DTO.
 * key: game_session:{userId}:{level}:{date}  TTL: 24시간
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionData {

    // key: round number (1, 2, 3)
    private Map<Integer, RoundResult> rounds = new HashMap<>();
    private int hearts = 0;
    private boolean gameOver = false;

    public void addRound(RoundResult result) {
        this.rounds.put(result.getRound(), result);
    }

    public Optional<RoundResult> getRound(int round) {
        return Optional.ofNullable(rounds.get(round));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundResult {
        private int round;
        private String wordLevelFeedback; // JSON 문자열
        private double totalScore;
        private double speedSimilarity;
        private double dynamicStressScore;
        private double boundaryToneScore;
    }
}

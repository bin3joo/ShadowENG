package com.bremenband.shadowengapi.domain.study.dto.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis에 임시 저장되는 평가 데이터 (step 1~3).
 * step 4 완료 시 전체 사이클을 DB에 일괄 커밋하고 Redis에서 삭제된다.
 * TTL 24시간 초과 시 Redis에서 자동 삭제 (중도 이탈 처리).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PendingEvaluation {

    private int    step;
    private String userTranscription;
    private String wordLevelFeedback;
    private String boundaryToneFeedback;
    private String dynamicStressFeedback;
    private double totalScore;
    private double wordAccuracy;
    private double prosodyAndStress;
    private double wordRhythmScore;
    private double boundaryToneScore;
    private double dynamicStressScore;
    private double speedSimilarity;
    private double pauseSimilarity;
}

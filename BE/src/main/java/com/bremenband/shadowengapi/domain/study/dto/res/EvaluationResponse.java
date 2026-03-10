package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "음성 평가 응답 DTO")
public record EvaluationResponse(

        @Schema(description = "문장 고유 ID", example = "1234")
        Long sentenceId,

        @Schema(description = "문장 시작 시간 (초)", example = "5.61")
        double startSec,

        @Schema(description = "문장 종료 시간 (초)", example = "10.78")
        double endSec,

        @Schema(description = "문장 지속 시간 (초)", example = "5.17")
        double durationSec,

        @Schema(description = "유저 음성 인식 결과")
        String userTranscription,

        @Schema(description = "세부 피드백")
        Details details,

        @Schema(description = "채점 결과")
        Scores scores

) {

    public record Details(
            List<WordLevelFeedback> wordLevelFeedback,
            BoundaryToneFeedback boundaryToneFeedback,
            DynamicStressFeedback dynamicStressFeedback
    ) {
    }

    public record WordLevelFeedback(
            String word,
            String status
    ) {
    }

    public record BoundaryToneFeedback(
            String status,
            String message
    ) {
    }

    public record DynamicStressFeedback(
            String status,
            String message
    ) {
    }

    public record Scores(
            double totalScore,
            double wordAccuracy,
            double prosodyAndStress,
            double wordRhythmScore,
            double boundaryToneScore,
            double dynamicStressScore,
            double speedSimilarity,
            double pauseSimilarity
    ) {
    }
}

package com.bremenband.shadowengapi.domain.game.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GameEvaluationResponse(
        String userTranscription,
        Details details,
        Scores scores,
        int round,
        int hearts,
        boolean gameOver,
        GameResult result  // null if !gameOver
) {
    public record Details(
            @Schema(name = "wordFeedback")
            List<WordLevelFeedback> wordFeedback,
            BoundaryToneFeedback boundaryToneFeedback,
            DynamicStressFeedback dynamicStressFeedback
    ) {
        public record WordLevelFeedback(String word, String status) {}
        public record BoundaryToneFeedback(String status) {}
        public record DynamicStressFeedback(String status) {}
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
    ) {}

    public record GameResult(
            double avgTotalScore,
            double avgWordRhythmScore,
            double avgDynamicStressScore,
            double avgWordAccuracy,
            double finalScore,
            int hearts
    ) {}
}

package com.bremenband.shadowengapi.domain.report.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학습 세션 레포트 응답 DTO")
public record ReportResponse(

        @Schema(description = "학습 세션 ID", example = "1234")
        Long sessionId,

        @Schema(description = "종합 점수")
        Scores scores,

        @Schema(description = "취약 문장 목록 (평균 totalScore < 70)")
        List<DifficultSentence> difficultSentences

) {

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

    public record DifficultSentence(
            Long sentenceId,
            String sentence
    ) {
    }
}

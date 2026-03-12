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
            @Schema(description = "단어별 피드백 목록")
            List<WordLevelFeedback> wordLevelFeedback,

            @Schema(description = "문장 끝 억양 피드백")
            BoundaryToneFeedback boundaryToneFeedback,

            @Schema(description = "강세 변화 피드백")
            DynamicStressFeedback dynamicStressFeedback
    ) {
    }

    public record WordLevelFeedback(
            @Schema(description = "단어", example = "got")
            String word,

            @Schema(description = "단어 상태 (good / rushed / dragged / missed / extra)", example = "good")
            String status
    ) {
    }

    public record BoundaryToneFeedback(
            @Schema(description = "억양 상태 (good / weak / opposite / short / no_ref_data)", example = "weak")
            String status
    ) {
    }

    public record DynamicStressFeedback(
            @Schema(description = "강세 상태 (good / monotone / exaggerated / flat / no_ref_data)", example = "monotone")
            String status
    ) {
    }

    public record Scores(
            @Schema(description = "종합 점수", example = "73.7") double totalScore,
            @Schema(description = "단어 정확도", example = "93.8") double wordAccuracy,
            @Schema(description = "억양/강세 종합", example = "37.6") double prosodyAndStress,
            @Schema(description = "단어 리듬", example = "73.0") double wordRhythmScore,
            @Schema(description = "문장 끝 억양", example = "55.8") double boundaryToneScore,
            @Schema(description = "강세 변화", example = "76.0") double dynamicStressScore,
            @Schema(description = "속도 유사도", example = "85.2") double speedSimilarity,
            @Schema(description = "포즈 유사도", example = "100.0") double pauseSimilarity
    ) {
    }
}

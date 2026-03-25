package com.bremenband.shadowengapi.domain.report.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "학습 세션 레포트 응답 DTO")
public record ReportResponse(

        @Schema(description = "리포트 ID", example = "1")
        Long reportId,

        @Schema(description = "학습 세션 ID", example = "1234")
        Long sessionId,

        @Schema(description = "레포트 생성 일시", example = "2025-03-23T14:30:00")
        LocalDateTime createdAt,

        @Schema(description = "학습 회차 (1: 최초 학습, 2: 첫 번째 복습, ...)", example = "1")
        int cycleNumber,

        @Schema(description = "세션 총 문장 수", example = "5")
        int totalSentenceCount,

        @Schema(description = "종합 점수")
        Scores scores,

        @Schema(description = "취약 문장 목록 (평균 totalScore 90점 미만, 낮은 순 최대 3개)")
        List<DifficultSentence> difficultSentences

) {

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

    public record DifficultSentence(
            @Schema(description = "문장 고유 ID", example = "42")
            Long sentenceId,

            @Schema(description = "문장 원문")
            String sentence,

            @Schema(description = "문장별 평균 totalScore", example = "45.3")
            double averageScore,

            @Schema(description = "문장 끝 억양 상태 (good / weak / opposite / short / no_ref_data)", example = "weak")
            String boundaryToneStatus,

            @Schema(description = "강세 변화 상태 (good / monotone / exaggerated / flat / no_ref_data)", example = "monotone")
            String dynamicStressStatus,

            @Schema(description = "단어별 피드백 목록")
            List<WordFeedback> wordFeedback
    ) {
        public record WordFeedback(
                @Schema(description = "단어", example = "got")
                String word,

                @Schema(description = "단어 상태 (good / rushed / dragged / missed / extra)", example = "rushed")
                String status
        ) {
        }
    }
}

package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문장 학습 데이터 응답 DTO")
public record SentenceLearningResponse(

        @Schema(description = "학습 단계 (1: 첫 학습 - 마스킹 없음, 2: 재학습 - 마스킹 포함)", example = "1")
        int step,

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "문장 고유 ID", example = "1234")
        Long sentenceId,

        @Schema(description = "문장 원문")
        String sentence,

        @Schema(description = "마스킹된 문장 (step 1이면 null, step 2이면 특정 단어가 _____로 치환된 문장)")
        String hiddenSentence,

        @Schema(description = "문장 시작 시간 (초)", example = "5.61")
        double startSec,

        @Schema(description = "문장 종료 시간 (초)", example = "10.78")
        double endSec,

        @Schema(description = "문장 지속 시간 (초)", example = "5.17")
        double durationSec,

        @Schema(description = "문장 학습 횟수 (step 4 완료 기준)", example = "0")
        int studyCount,

        @Schema(description = "세션 복습 모드 여부 (사용자가 '복습하기' 클릭 시 true)", example = "false")
        boolean isReviewing,

        @Schema(description = "이 문장의 복습 필요 여부 (studyCount <= cycleCount)", example = "false")
        boolean needsReview

) {
}

package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 학습 중인 세션 정보")
public record LatestActiveSessionResponse(

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "영상 썸네일 URL (standard 640x480)", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg")
        String thumbnailUrl,

        @Schema(description = "영상 제목", example = "Rick Astley - Never Gonna Give You Up")
        String videoTitle,

        @Schema(description = "세션 전체 문장 수", example = "8")
        long totalSentences,

        @Schema(description = "평가 완료된 문장 수 (1회 이상 평가)", example = "5")
        long completedSentences

) {
}

package com.bremenband.shadowengapi.domain.study.dto.res;

import com.bremenband.shadowengapi.domain.youtube.dto.res.ThumbnailInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학습 중인 세션 정보")
public record ActiveSessionResponse(

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "세션 이름", example = "Friends Season 1 Episode 1")
        String name,

        @Schema(description = "영상 제목", example = "Friends Season 1 Episode 1")
        String videoTitle,

        @Schema(description = "영상 썸네일 (standard 사이즈, 640x480)")
        ThumbnailInfo thumbnails,

        @Schema(description = "세션 전체 문장 수", example = "8")
        long totalSentences,

        @Schema(description = "평가 완료된 문장 수 (1회 이상 평가)", example = "5")
        long completedSentences

) {
}

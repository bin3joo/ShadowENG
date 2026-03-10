package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 학습 중인 세션 정보")
public record LatestActiveSessionResponse(

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "영상 썸네일 목록")
        ThumbnailsResponse thumbnails,

        @Schema(description = "학습 진행률 (%)", example = "40")
        int progressRate

) {
}

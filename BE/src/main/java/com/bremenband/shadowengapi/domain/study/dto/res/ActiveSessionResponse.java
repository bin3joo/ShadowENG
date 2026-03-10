package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학습 중인 세션 정보")
public record ActiveSessionResponse(

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "영상 썸네일 (standard 사이즈, 640x480)")
        ThumbnailInfo thumbnails,

        @Schema(description = "학습 진행률 (%)", example = "40")
        int progressRate

) {
}

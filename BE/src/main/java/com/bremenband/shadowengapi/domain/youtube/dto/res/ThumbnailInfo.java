package com.bremenband.shadowengapi.domain.youtube.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "썸네일 정보")
public record ThumbnailInfo(

        @Schema(description = "썸네일 URL")
        String url,

        @Schema(description = "썸네일 가로 크기 (px)")
        int width,

        @Schema(description = "썸네일 세로 크기 (px)")
        int height

) {
}

package com.bremenband.shadowengapi.domain.youtube.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유튜브 썸네일 사이즈별 정보")
public record ThumbnailsResponse(

        @JsonProperty("default")
        @Schema(description = "기본 썸네일 (120x90)")
        ThumbnailInfo defaultThumbnail,

        @Schema(description = "중간 썸네일 (320x180)")
        ThumbnailInfo medium,

        @Schema(description = "고화질 썸네일 (480x360)")
        ThumbnailInfo high,

        @Schema(description = "표준 썸네일 (640x480)")
        ThumbnailInfo standard,

        @Schema(description = "최고화질 썸네일 (1280x720)")
        ThumbnailInfo maxres

) {
    public static ThumbnailsResponse from(String videoId) {
        String base = "https://i.ytimg.com/vi/" + videoId + "/";
        return new ThumbnailsResponse(
                new ThumbnailInfo(base + "default.jpg", 120, 90),
                new ThumbnailInfo(base + "mqdefault.jpg", 320, 180),
                new ThumbnailInfo(base + "hqdefault.jpg", 480, 360),
                new ThumbnailInfo(base + "sddefault.jpg", 640, 480),
                new ThumbnailInfo(base + "maxresdefault.jpg", 1280, 720)
        );
    }
}

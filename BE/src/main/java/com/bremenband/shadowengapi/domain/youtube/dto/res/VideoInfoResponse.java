package com.bremenband.shadowengapi.domain.youtube.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유튜브 영상 정보 응답 DTO")
public record VideoInfoResponse(

        @Schema(description = "유튜브 영상 고유 ID", example = "dQw4w9WgXcQ")
        String videoId,

        @Schema(description = "임베드 URL", example = "https://www.youtube.com/embed/dQw4w9WgXcQ")
        String embedUrl,

        @Schema(description = "일반 시청 URL (embed 불가 시 fallback)", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        String watchUrl,

        @Schema(description = "영상 제목", example = "Rick Astley - Never Gonna Give You Up")
        String title,

        @Schema(description = "썸네일 URL", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg")
        String thumbnailUrl,

        @Schema(description = "영상 총 길이 (초)", example = "212")
        long duration,

        @Schema(description = "채널명", example = "Rick Astley")
        String channelTitle

) {
}

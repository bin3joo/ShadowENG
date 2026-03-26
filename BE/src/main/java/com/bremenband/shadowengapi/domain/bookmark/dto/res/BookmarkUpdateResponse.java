package com.bremenband.shadowengapi.domain.bookmark.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 상태 변경 응답 DTO")
public record BookmarkUpdateResponse(

        @Schema(description = "학습 세션 ID", example = "12345")
        Long sessionId,

        @JsonProperty("isBookmarked")
        @Schema(description = "현재 북마크 상태", example = "true")
        Boolean isBookmarked

) {}

package com.bremenband.shadowengapi.domain.bookmark.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "북마크 상태 변경 요청 DTO")
public record BookmarkUpdateRequest(

        @NotNull
        @Schema(description = "북마크 설정(true) 또는 해제(false)", example = "true")
        Boolean isBookmarked

) {}

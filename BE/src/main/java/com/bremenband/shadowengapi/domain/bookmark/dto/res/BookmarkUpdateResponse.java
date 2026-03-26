package com.bremenband.shadowengapi.domain.bookmark.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 상태 변경 응답 DTO")
public record BookmarkUpdateResponse(

        @Schema(description = "문장 ID", example = "1234")
        Long sentenceId,

        @Schema(description = "문장 내용", example = "I got it bad.")
        String sentence,

        @JsonProperty("isBookmarked")
        @Schema(description = "현재 북마크 상태", example = "true")
        Boolean isBookmarked

) {}

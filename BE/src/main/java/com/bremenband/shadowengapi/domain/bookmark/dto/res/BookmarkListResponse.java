package com.bremenband.shadowengapi.domain.bookmark.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "북마크 목록 조회 응답 DTO")
public record BookmarkListResponse(

        @Schema(description = "북마크 목록")
        List<BookmarkItem> bookmarks

) {

    @Schema(description = "북마크 항목")
    public record BookmarkItem(

            @Schema(description = "학습 세션 ID", example = "12345")
            Long sessionId,

            @Schema(description = "영상 제목", example = "Never Gonna Give You Up")
            String videoTitle,

            @Schema(description = "영상 썸네일 URL")
            String thumbnailUrl,

            @Schema(description = "세션 시작 시간 (초)", example = "30.0")
            double startSec,

            @Schema(description = "세션 종료 시간 (초)", example = "60.0")
            double endSec,

            @Schema(description = "총 문장 수", example = "8")
            long totalSentences,

            @Schema(description = "학습 완료된 문장 수 (step4 완료 기준)", example = "5")
            long completedSentences

    ) {}
}

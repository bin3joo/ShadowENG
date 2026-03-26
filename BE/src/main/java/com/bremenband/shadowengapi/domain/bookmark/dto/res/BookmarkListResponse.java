package com.bremenband.shadowengapi.domain.bookmark.dto.res;

import com.bremenband.shadowengapi.domain.bookmark.entity.Bookmark;
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
            double endSec

    ) {
        public static BookmarkItem from(Bookmark bookmark) {
            return new BookmarkItem(
                    bookmark.getStudySession().getId(),
                    bookmark.getStudySession().getVideo().getTitle(),
                    bookmark.getStudySession().getVideo().getThumbnailUrl(),
                    bookmark.getStudySession().getStartSec(),
                    bookmark.getStudySession().getEndSec()
            );
        }
    }
}

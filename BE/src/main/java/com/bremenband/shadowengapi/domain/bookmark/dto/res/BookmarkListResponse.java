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

            @Schema(description = "문장 ID", example = "1234")
            Long sentenceId,

            @Schema(description = "문장 내용", example = "I got it bad.")
            String sentence,

            @Schema(description = "학습 세션 ID", example = "12345")
            Long sessionId

    ) {
        public static BookmarkItem from(Bookmark bookmark) {
            return new BookmarkItem(
                    bookmark.getSentence().getId(),
                    bookmark.getSentence().getContent(),
                    bookmark.getSentence().getStudySession().getId()
            );
        }
    }
}

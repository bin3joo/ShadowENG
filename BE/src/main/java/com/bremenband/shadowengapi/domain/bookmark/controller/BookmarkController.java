package com.bremenband.shadowengapi.domain.bookmark.controller;

import com.bremenband.shadowengapi.domain.bookmark.dto.req.BookmarkUpdateRequest;
import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkListResponse;
import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkUpdateResponse;
import com.bremenband.shadowengapi.domain.bookmark.service.BookmarkService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "북마크 API", description = "북마크 관련 기능을 위한 REST API")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping("/bookmarks")
    @Operation(
            summary = "사용자의 북마크 목록 전체 조회",
            description = "요청 헤더의 Access Token을 통해 인증된 사용자가 북마크한 문장 목록을 조회합니다."
    )
    public ApiResponse<BookmarkListResponse> getBookmarks(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(bookmarkService.getBookmarks(userId));
    }

    @PatchMapping(value = "/bookmarks/{sentenceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "문장 북마크 상태 변경",
            description = "특정 문장의 북마크 상태를 변경(추가 또는 해제)합니다."
    )
    public ApiResponse<BookmarkUpdateResponse> updateBookmark(
            @Parameter(description = "북마크 상태를 변경할 문장의 고유 ID", example = "123")
            @PathVariable Long sentenceId,
            @Valid @RequestBody BookmarkUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(bookmarkService.updateBookmark(userId, sentenceId, request.isBookmarked()));
    }

}

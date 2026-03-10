package com.bremenband.shadowengapi.domain.youtube.controller;

import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/youtube")
@RequiredArgsConstructor
@Tag(name = "유튜브 관련 API", description = "Youtube API와의 통신과 관련된 기능을 위한 REST API")
public class YoutubeController {

    private final YoutubeService youtubeService;

    @GetMapping
    @Operation(
            summary = "영상 정보 조회",
            description = "전달받은 유튜브 URL을 분석하여 영상의 ID, 제목, 썸네일 등의 상세 정보를 조회하고, Embed URL을 반환합니다."
    )
    public ApiResponse<VideoInfoResponse> getVideo(
            @Parameter(description = "조회할 유튜브 영상의 원본 URL (URL 인코딩 필요)", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            @RequestParam String url,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(youtubeService.getVideo(url));
    }
}

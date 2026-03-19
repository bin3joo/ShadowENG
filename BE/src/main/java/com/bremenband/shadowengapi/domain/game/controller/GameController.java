package com.bremenband.shadowengapi.domain.game.controller;

import com.bremenband.shadowengapi.domain.game.dto.res.*;
import com.bremenband.shadowengapi.domain.game.service.GameService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "게임 API", description = "잉무를 꼬셔라 게임 관련 REST API")
public class GameController {

    private final GameService gameService;

    @GetMapping("/today")
    @Operation(
            summary = "오늘의 게임 현황 조회",
            description = "레벨별 해금 상태와 오늘의 최고 기록을 반환합니다."
    )
    public ApiResponse<GameTodayResponse> getToday(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameService.getToday(userId));
    }

    @GetMapping("/levels/{level}/rounds/{round}")
    @Operation(
            summary = "라운드별 문장 조회",
            description = "round 1: 전체 문장, round 2: 마스킹 문장, round 3: null."
    )
    public ApiResponse<GameRoundResponse> getRoundSentence(
            @Parameter(description = "레벨 (1~3)") @PathVariable @Min(1) @Max(3) int level,
            @Parameter(description = "라운드 (1~3)") @PathVariable @Min(1) @Max(3) int round,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameService.getRoundSentence(userId, level, round));
    }

    @PostMapping(value = "/levels/{level}/rounds/{round}/evaluate",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "라운드 음성 평가",
            description = "음성 파일을 제출하고 발음을 평가합니다. " +
                          "총점 70점 미만이거나 round 3 완료 시 게임이 종료됩니다."
    )
    public ApiResponse<GameEvaluationResponse> evaluate(
            @Parameter(description = "레벨 (1~3)") @PathVariable @Min(1) @Max(3) int level,
            @Parameter(description = "라운드 (1~3)") @PathVariable @Min(1) @Max(3) int round,
            @Parameter(description = "녹음 음성 파일") @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameService.evaluate(userId, level, round, file));
    }

    @GetMapping("/leaderboard")
    @Operation(
            summary = "리더보드 조회",
            description = "사용자 티어 내 순위 정보를 조회합니다. freeze 상태의 사용자는 접근 불가합니다."
    )
    public ApiResponse<GameLeaderboardResponse> getLeaderboard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameService.getLeaderboard(userId));
    }

    @GetMapping("/profile")
    @Operation(
            summary = "내 게임 프로필 조회",
            description = "현재 티어, 주간 점수, freeze 여부를 조회합니다."
    )
    public ApiResponse<GameProfileResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameService.getProfile(userId));
    }
}

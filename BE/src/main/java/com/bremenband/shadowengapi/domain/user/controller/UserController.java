package com.bremenband.shadowengapi.domain.user.controller;

import com.bremenband.shadowengapi.domain.user.dto.req.UpdateNicknameRequest;
import com.bremenband.shadowengapi.domain.user.dto.res.MainResponse;
import com.bremenband.shadowengapi.domain.user.dto.res.UserDashboardResponse;
import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "회원 관련 API", description = "회원 정보와 관련된 기능을 위한 REST API")
public class UserController {

    private final UserService userService;

    @GetMapping("/main")
    @Operation(
            summary = "메인 화면 조회",
            description = "이번주 학습 요일, 최근 학습 세션, 게임 요약 정보를 한 번에 반환합니다."
    )
    public ApiResponse<MainResponse> getMain(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(userService.getMain(userId));
    }

    @GetMapping("/dashboard")
    @Operation(
            summary = "사용자 대시보드 조회",
            description = "최장 연속 출석일, 총 출석일, 총 학습 문장 수, 총 학습 시간, 학습 날짜 목록, 레포트 생성 날짜 목록을 반환합니다."
    )
    public ApiResponse<UserDashboardResponse> getDashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(userService.getDashboard(userId));
    }

    @GetMapping("/me")
    @Operation(
            summary = "사용자 정보 조회",
            description = "요청 헤더의 Access Token을 통해 인증된 사용자의 상세 정보를 조회합니다."
    )
    public ApiResponse<UserInfoResponse> getUserData(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(userService.getUserInfo(userId));
    }

    @PatchMapping("/nickname")
    @Operation(
            summary = "닉네임 변경",
            description = "인증된 사용자의 닉네임을 변경합니다."
    )
    public ApiResponse<Void> updateNickname(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UpdateNicknameRequest request
    ) {
        userService.updateNickname(userId, request.nickname());
        return ApiResponse.success(null);
    }

}

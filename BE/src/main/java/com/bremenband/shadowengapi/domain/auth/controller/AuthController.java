package com.bremenband.shadowengapi.domain.auth.controller;

import com.bremenband.shadowengapi.domain.auth.dto.req.GuestLoginRequest;
import com.bremenband.shadowengapi.domain.auth.dto.req.KakaoLoginRequest;
import com.bremenband.shadowengapi.domain.auth.dto.req.TokenRefreshRequest;
import com.bremenband.shadowengapi.domain.auth.dto.res.GuestLoginResponse;
import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.domain.auth.service.AuthService;
import com.bremenband.shadowengapi.domain.auth.service.GuestAuthService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "소셜 로그인 API", description = "소셜 로그인 & 로그아웃을 위한 REST API")
public class AuthController {

    private final AuthService      authService;
    private final GuestAuthService guestAuthService;

    @PostMapping(value = "/login/guest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "게스트 로그인",
            description = "디바이스 ID(UUID)로 게스트 계정을 생성하거나 기존 게스트 계정으로 로그인합니다. " +
                    "최초 요청 시 게스트 계정이 자동 생성됩니다."
    )
    public ApiResponse<GuestLoginResponse> guestLogin(@Valid @RequestBody GuestLoginRequest request) {
        return ApiResponse.success(guestAuthService.guestLogin(request.deviceId()));
    }

    @PostMapping(value = "/login/kakao", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "카카오 로그인",
            description = "클라이언트에서 카카오 SDK를 통해 발급받은 카카오 인가 코드를 Request Body로 전달받아 " +
                    "유저 정보를 확인하고, 서비스 자체 JWT(액세스 및 리프레시 토큰)를 발급합니다.")
    public ApiResponse<?> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        throw new CustomException(ErrorCode.NOT_IMPLEMENTED);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "토큰 재발급",
            description = "리프레시 토큰을 검증하여 새로운 액세스 토큰과 리프레시 토큰을 발급합니다."
    )
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "로그아웃",
            description = "서버에 저장된 리프레시 토큰을 삭제하여 로그아웃 처리합니다."
    )
    public ApiResponse<?> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        authService.logout(userId);
        return ApiResponse.success();
    }
}

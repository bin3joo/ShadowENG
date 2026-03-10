package com.bremenband.shadowengapi.domain.user.controller;

import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "회원 관련 API", description = "회원 정보와 관련된 기능을 위한 REST API")
public class UserController {

    private final UserService userService;

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

}

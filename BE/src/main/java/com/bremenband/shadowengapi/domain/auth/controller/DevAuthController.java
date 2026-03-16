package com.bremenband.shadowengapi.domain.auth.controller;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.domain.auth.facade.DevAuthFacade;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "개발용 인증 API", description = "개발/테스트 환경에서만 사용 가능한 토큰 발급 API")
public class DevAuthController {

    private final DevAuthFacade devAuthFacade;

    @PostMapping("/login/dev")
    @Operation(
            summary = "[DEV] 테스트용 토큰 발급",
            description = "카카오 OAuth 없이 userId만으로 JWT를 발급합니다. 개발 환경(dev 프로파일)에서만 활성화됩니다."
    )
    public ApiResponse<TokenResponse> devLogin(@RequestParam Long userId) {
        return ApiResponse.success(devAuthFacade.devLogin(userId));
    }
}

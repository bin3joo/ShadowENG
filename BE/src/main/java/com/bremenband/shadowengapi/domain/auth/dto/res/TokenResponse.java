package com.bremenband.shadowengapi.domain.auth.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 발급 응답 DTO")
public record TokenResponse(

        @Schema(description = "액세스 토큰")
        String accessToken,

        @Schema(description = "리프레시 토큰")
        String refreshToken

) {}

package com.bremenband.shadowengapi.domain.auth.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 재발급 요청 DTO")
public record TokenRefreshRequest(

        @NotBlank
        @Schema(description = "리프레시 토큰")
        String refreshToken

) {}

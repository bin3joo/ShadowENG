package com.bremenband.shadowengapi.domain.auth.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "게스트 로그인 요청 DTO")
public record GuestLoginRequest(

        @Schema(description = "앱 설치 시 생성된 디바이스 고유 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank
        String deviceId

) {}

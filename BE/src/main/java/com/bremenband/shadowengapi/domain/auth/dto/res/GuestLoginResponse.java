package com.bremenband.shadowengapi.domain.auth.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게스트 로그인 응답 DTO")
public record GuestLoginResponse(

        @Schema(description = "액세스 토큰")
        String accessToken,

        @Schema(description = "리프레시 토큰")
        String refreshToken,

        @JsonProperty("isNew")
        @Schema(description = "최초 로그인(신규 계정 생성) 여부")
        boolean isNew

) {}

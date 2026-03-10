package com.bremenband.shadowengapi.domain.auth.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class KakaoLoginRequest {

    @Schema(description = "카카오 SDK를 통해 발급받은 인가 코드", example = "qWeRtYuIoPaSdFgHjKlZxCvBnM1234567890")
    private String authorizationCode;

}

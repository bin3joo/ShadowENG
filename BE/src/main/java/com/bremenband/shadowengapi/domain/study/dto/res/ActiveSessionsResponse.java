package com.bremenband.shadowengapi.domain.study.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학습 중인 세션 전체 조회 응답 DTO")
public record ActiveSessionsResponse(

        @JsonProperty("ActiveSessions")
        @Schema(description = "학습 중인 세션 목록")
        List<ActiveSessionResponse> activeSessions

) {
}

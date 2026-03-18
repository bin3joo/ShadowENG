package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학습 세션 전체 조회 응답 DTO")
public record ActiveSessionsResponse(

        @Schema(description = "학습 중인 세션 목록 (ACTIVE)")
        List<ActiveSessionResponse> activeSessions,

        @Schema(description = "학습 완료된 세션 목록 (COMPLETED)")
        List<ActiveSessionResponse> completedSessions

) {
}

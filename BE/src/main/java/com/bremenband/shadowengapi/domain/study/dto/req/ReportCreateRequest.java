package com.bremenband.shadowengapi.domain.study.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "레포트 생성 요청 DTO")
public record ReportCreateRequest(

        @Schema(description = "학습 세션 ID", example = "12345")
        @NotNull(message = "sessionId는 필수입니다.")
        Long sessionId

) {
}

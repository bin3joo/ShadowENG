package com.bremenband.shadowengapi.domain.study.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "음성 평가 요청 DTO (multipart/form-data의 request 파트)")
public record EvaluationRequest(

        @Schema(description = "현재 문장의 학습 단계 (1~4)", example = "3")
        @NotNull(message = "step은 필수입니다.")
        @Min(value = 1, message = "step은 1 이상이어야 합니다.")
        @Max(value = 4, message = "step은 4 이하이어야 합니다.")
        Integer step

) {
}

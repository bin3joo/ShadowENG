package com.bremenband.shadowengapi.domain.study.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "문장 학습 데이터 요청 DTO")
public record SentenceLearningRequest(

        @Schema(description = "학습 단계 (1: 첫 학습 - 마스킹 없음, 2: 재학습 - 마스킹 포함)", example = "1")
        @NotNull(message = "step은 필수입니다.")
        @Min(value = 1, message = "step은 1 이상이어야 합니다.")
        @Max(value = 4, message = "step은 4 이하이어야 합니다.")
        Integer step

) {
}

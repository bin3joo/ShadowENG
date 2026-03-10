package com.bremenband.shadowengapi.domain.study.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "학습 세션 생성 요청 DTO")
public record StudySessionCreateRequest(

        @Schema(description = "학습할 영상의 임베드 URL", example = "https://www.youtube.com/embed/dQw4w9WgXcQ")
        @NotBlank(message = "embedUrl은 필수입니다.")
        String embedUrl,

        @Schema(description = "학습 구간 시작점 (초)", example = "15.5")
        @NotNull(message = "startSec은 필수입니다.")
        @Positive(message = "startSec은 0보다 커야 합니다.")
        Double startSec,

        @Schema(description = "학습 구간 종료점 (초)", example = "45.0")
        @NotNull(message = "endSec은 필수입니다.")
        @Positive(message = "endSec은 0보다 커야 합니다.")
        Double endSec

) {
}

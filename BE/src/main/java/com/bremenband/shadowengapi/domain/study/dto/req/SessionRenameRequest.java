package com.bremenband.shadowengapi.domain.study.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "세션 이름 변경 요청 DTO")
public record SessionRenameRequest(

        @NotBlank(message = "세션 이름은 비어있을 수 없습니다.")
        @Size(max = 100, message = "세션 이름은 최대 100자까지 입력 가능합니다.")
        @Schema(description = "변경할 세션 이름", example = "영어 공부 세션")
        String name

) {}

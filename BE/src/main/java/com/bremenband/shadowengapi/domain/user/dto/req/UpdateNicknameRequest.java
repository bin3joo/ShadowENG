package com.bremenband.shadowengapi.domain.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청 DTO")
public record UpdateNicknameRequest(

        @Schema(description = "변경할 닉네임", example = "새닉네임")
        @NotBlank
        @Size(max = 20)
        String nickname

) {}

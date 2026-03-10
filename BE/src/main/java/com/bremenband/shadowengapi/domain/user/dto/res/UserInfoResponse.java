package com.bremenband.shadowengapi.domain.user.dto.res;

import com.bremenband.shadowengapi.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "사용자 정보 응답 DTO")
public record UserInfoResponse(

        @Schema(description = "사용자 ID", example = "12345")
        Long userId,

        @Schema(description = "닉네임", example = "브레맨")
        String nickname,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "방문 횟수", example = "15")
        int visitedCount,

        @Schema(description = "가입일시", example = "2025-08-01T10:00:00")
        LocalDateTime createdAt

) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getVisitedCount(),
                user.getCreatedAt()
        );
    }
}

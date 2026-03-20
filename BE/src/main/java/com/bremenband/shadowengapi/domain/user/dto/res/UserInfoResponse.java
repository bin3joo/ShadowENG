package com.bremenband.shadowengapi.domain.user.dto.res;

import com.bremenband.shadowengapi.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자 정보 응답 DTO")
public record UserInfoResponse(

        @Schema(description = "사용자 ID", example = "12345")
        Long userId,

        @Schema(description = "닉네임", example = "브레맨")
        String nickname,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "총 출석일 (앱 방문 기준)", example = "15")
        int totalVisitedDays,

        @Schema(description = "총 학습일 (평가 완료 기준)", example = "10")
        int totalStudyDays,

        @Schema(description = "최장 연속 학습일", example = "5")
        int longestStreak,

        @Schema(description = "학습한 날짜 목록 (달력 표시용)", example = "[\"2025-08-01\",\"2025-08-02\"]")
        List<LocalDate> studyDates,

        @Schema(description = "가입일시", example = "2025-08-01T10:00:00")
        LocalDateTime createdAt

) {
}

package com.bremenband.shadowengapi.domain.user.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "사용자 대시보드 응답 DTO")
public record UserDashboardResponse(

        @Schema(description = "최장 연속 출석일", example = "7")
        int longestStreak,

        @Schema(description = "총 출석일", example = "30")
        int totalAttendanceDays,

        @Schema(description = "총 학습 완료 문장 수", example = "42")
        long totalStudiedSentences,

        @Schema(description = "총 학습 시간 (초)", example = "3600")
        long totalStudyTimeSeconds,

        @Schema(description = "한 문장 이상 학습한 날짜 목록")
        List<LocalDate> studyDates,

        @Schema(description = "레포트가 생성된 날짜 목록 (날짜별 레포트 정보 포함)")
        List<ReportDateEntry> reportDates

) {

    @Schema(description = "날짜별 레포트 항목")
    public record ReportDateEntry(

            @Schema(description = "레포트 생성 날짜")
            LocalDate date,

            @Schema(description = "해당 날짜에 생성된 레포트 목록")
            List<ReportInfo> reports

    ) {}

    @Schema(description = "레포트 요약 정보")
    public record ReportInfo(

            @Schema(description = "레포트 ID", example = "1234")
            Long reportId,

            @Schema(description = "학습 세션 ID", example = "5678")
            Long sessionId,

            @Schema(description = "세션 영상 썸네일 URL")
            String thumbnailUrl,

            @Schema(description = "영상 제목")
            String videoTitle,

            @Schema(description = "리포트 종합 점수", example = "82.5")
            double totalScore

    ) {}
}

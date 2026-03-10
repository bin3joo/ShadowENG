package com.bremenband.shadowengapi.domain.report.controller;

import com.bremenband.shadowengapi.domain.report.dto.res.DailyReportResponse;
import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.service.ReportService;
import com.bremenband.shadowengapi.domain.study.dto.req.ReportCreateRequest;
import com.bremenband.shadowengapi.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "리포트 API", description = "리포트와 관련된 기능을 위한 REST API")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/study-sessions/{sessionId}/reports")
    @Operation(
            summary = "학습 세션 레포트 생성",
            description = "특정 학습 세션의 평가 결과를 종합하여 레포트를 생성합니다. 평균 점수 및 취약 문장(평균 totalScore < 70)을 포함합니다."
    )
    public ApiResponse<ReportResponse> createSessionReport(
            @Parameter(description = "레포트를 생성할 학습 세션 ID", example = "12345")
            @PathVariable Long sessionId,
            @Valid @RequestBody ReportCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(reportService.createReport(sessionId, userId));
    }

    @GetMapping("/study-sessions/{sessionId}/reports")
    @Operation(
            summary = "학습 세션 레포트 조회",
            description = "특정 학습 세션의 레포트를 조회합니다."
    )
    public ApiResponse<ReportResponse> getSessionReport(
            @Parameter(description = "조회할 학습 세션 ID", example = "12345")
            @PathVariable Long sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(reportService.getReport(sessionId, userId));
    }

    @GetMapping("/reports/daily")
    @Operation(
            summary = "데일리 학습 리포트 조회",
            description = "인증된 사용자의 일자별 학습 데이터를 조회합니다."
    )
    public ApiResponse<DailyReportResponse> getDailyReport(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(reportService.getDailyReport(userId));
    }
}

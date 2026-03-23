package com.bremenband.shadowengapi.domain.report.controller;

import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.service.ReportService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class ReportGetControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReportService reportService;

    private static final ReportResponse.Scores SCORES =
            new ReportResponse.Scores(73.7, 93.8, 37.6, 73.0, 55.8, 76.0, 85.2, 100.0);

    // ── 목록 조회 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("레포트 목록 조회 시 세션의 모든 레포트를 배열로 반환한다")
    void getSessionReports_성공_200() throws Exception {
        // given
        Long sessionId = 1L;

        LocalDateTime now = LocalDateTime.of(2025, 3, 23, 14, 30, 0);
        List<ReportResponse> response = List.of(
                new ReportResponse(2L, sessionId, now, SCORES,
                        List.of(new ReportResponse.DifficultSentence(10L, "I got it bad.", 55.0, "weak", "monotone", List.of()))),
                new ReportResponse(1L, sessionId, now, SCORES, List.of())
        );

        given(reportService.getReports(eq(sessionId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].reportId").value(2))
                .andExpect(jsonPath("$.data[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data[0].scores.totalScore").value(73.7));

        then(reportService).should(times(1)).getReports(eq(sessionId), any());
    }

    @Test
    @DisplayName("레포트가 없는 세션은 빈 배열을 반환한다")
    void getSessionReports_레포트없음_빈배열() throws Exception {
        // given
        Long sessionId = 2L;
        given(reportService.getReports(eq(sessionId), any())).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── 단건 조회 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("레포트 단건 조회 시 점수와 취약 문장을 포함한 200을 반환한다")
    void getSessionReport_성공_200() throws Exception {
        // given
        Long sessionId = 1L;
        Long reportId  = 100L;

        ReportResponse response = new ReportResponse(
                reportId, sessionId, LocalDateTime.of(2025, 3, 23, 14, 30, 0), SCORES,
                List.of(new ReportResponse.DifficultSentence(10L, "I got it bad.", 55.0, "weak", "monotone", List.of()))
        );

        given(reportService.getReport(eq(sessionId), eq(reportId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports/{reportId}", sessionId, reportId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.scores.totalScore").value(73.7))
                .andExpect(jsonPath("$.data.difficultSentences.length()").value(1))
                .andExpect(jsonPath("$.data.difficultSentences[0].sentenceId").value(10));

        then(reportService).should(times(1)).getReport(eq(sessionId), eq(reportId), any());
    }

    @Test
    @DisplayName("존재하지 않는 레포트 조회 시 404를 반환한다")
    void getSessionReport_레포트없음_404() throws Exception {
        // given
        Long sessionId = 999L;
        Long reportId  = 1L;

        given(reportService.getReport(eq(sessionId), eq(reportId), any()))
                .willThrow(new CustomException(ErrorCode.REPORT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports/{reportId}", sessionId, reportId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.REPORT_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.REPORT_NOT_FOUND.getMessage()));

        then(reportService).should(times(1)).getReport(eq(sessionId), eq(reportId), any());
    }
}

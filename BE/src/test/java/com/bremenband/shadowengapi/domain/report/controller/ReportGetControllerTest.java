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

    @Test
    @DisplayName("레포트가 존재하면 점수와 취약 문장을 포함한 200을 반환한다")
    void getSessionReport_성공_200() throws Exception {
        // given
        Long sessionId = 1L;

        ReportResponse response = new ReportResponse(
                sessionId,
                new ReportResponse.Scores(73.7, 93.8, 37.6, 73.0, 55.8, 76.0, 85.2, 100.0),
                List.of(new ReportResponse.DifficultSentence(10L, "I got it bad."))
        );

        given(reportService.getReport(eq(sessionId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.scores.totalScore").value(73.7))
                .andExpect(jsonPath("$.data.scores.wordAccuracy").value(93.8))
                .andExpect(jsonPath("$.data.scores.prosodyAndStress").value(37.6))
                .andExpect(jsonPath("$.data.scores.wordRhythmScore").value(73.0))
                .andExpect(jsonPath("$.data.scores.boundaryToneScore").value(55.8))
                .andExpect(jsonPath("$.data.scores.dynamicStressScore").value(76.0))
                .andExpect(jsonPath("$.data.scores.speedSimilarity").value(85.2))
                .andExpect(jsonPath("$.data.scores.pauseSimilarity").value(100.0))
                .andExpect(jsonPath("$.data.difficultSentences").isArray())
                .andExpect(jsonPath("$.data.difficultSentences.length()").value(1))
                .andExpect(jsonPath("$.data.difficultSentences[0].sentenceId").value(10))
                .andExpect(jsonPath("$.data.difficultSentences[0].sentence").value("I got it bad."));

        then(reportService).should(times(1)).getReport(eq(sessionId), any());
    }

    @Test
    @DisplayName("취약 문장이 없는 레포트는 빈 배열과 200을 반환한다")
    void getSessionReport_취약문장없음_200() throws Exception {
        // given
        Long sessionId = 2L;

        ReportResponse response = new ReportResponse(
                sessionId,
                new ReportResponse.Scores(88.0, 90.0, 85.0, 87.0, 89.0, 86.0, 92.0, 95.0),
                List.of()
        );

        given(reportService.getReport(eq(sessionId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difficultSentences").isArray())
                .andExpect(jsonPath("$.data.difficultSentences.length()").value(0));
    }

    @Test
    @DisplayName("레포트가 없으면 404를 반환한다")
    void getSessionReport_레포트없음_404() throws Exception {
        // given
        Long sessionId = 999L;

        given(reportService.getReport(eq(sessionId), any()))
                .willThrow(new CustomException(ErrorCode.REPORT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}/reports", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.REPORT_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.REPORT_NOT_FOUND.getMessage()));

        then(reportService).should(times(1)).getReport(eq(sessionId), any());
    }
}

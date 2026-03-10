package com.bremenband.shadowengapi.domain.report.controller;

import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.service.ReportService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class ReportControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockitoBean private ReportService reportService;

    @Test
    @DisplayName("유효한 요청으로 레포트 생성 시 점수와 취약 문장을 포함한 200을 반환한다")
    void createSessionReport_성공_200() throws Exception {
        // given
        Long sessionId = 1L;
        Long userId    = 1L;

        ReportResponse response = new ReportResponse(
                sessionId,
                new ReportResponse.Scores(72.5, 90.0, 80.0, 75.0, 85.0, 70.0, 88.0, 95.0),
                List.of(new ReportResponse.DifficultSentence(10L, "I got it bad."))
        );

        given(reportService.createReport(eq(sessionId), any())).willReturn(response);

        String body = objectMapper.writeValueAsString(Map.of("sessionId", sessionId));

        // when & then
        mockMvc.perform(post("/study-sessions/{sessionId}/reports", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.scores.totalScore").value(72.5))
                .andExpect(jsonPath("$.data.scores.wordAccuracy").value(90.0))
                .andExpect(jsonPath("$.data.scores.prosodyAndStress").value(80.0))
                .andExpect(jsonPath("$.data.scores.speedSimilarity").value(88.0))
                .andExpect(jsonPath("$.data.difficultSentences").isArray())
                .andExpect(jsonPath("$.data.difficultSentences.length()").value(1))
                .andExpect(jsonPath("$.data.difficultSentences[0].sentenceId").value(10))
                .andExpect(jsonPath("$.data.difficultSentences[0].sentence").value("I got it bad."));

        then(reportService).should(times(1)).createReport(eq(sessionId), any());
    }

    @Test
    @DisplayName("평가 결과가 없으면 400을 반환한다")
    void createSessionReport_평가없음_400() throws Exception {
        // given
        Long sessionId = 1L;

        given(reportService.createReport(eq(sessionId), any()))
                .willThrow(new CustomException(ErrorCode.NO_EVALUATIONS_FOR_REPORT));

        String body = objectMapper.writeValueAsString(Map.of("sessionId", sessionId));

        // when & then
        mockMvc.perform(post("/study-sessions/{sessionId}/reports", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_EVALUATIONS_FOR_REPORT.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.NO_EVALUATIONS_FOR_REPORT.getMessage()));
    }

    @Test
    @DisplayName("세션이 없으면 404를 반환한다")
    void createSessionReport_세션없음_404() throws Exception {
        // given
        Long sessionId = 999L;

        given(reportService.createReport(eq(sessionId), any()))
                .willThrow(new CustomException(ErrorCode.SESSION_NOT_FOUND));

        String body = objectMapper.writeValueAsString(Map.of("sessionId", sessionId));

        // when & then
        mockMvc.perform(post("/study-sessions/{sessionId}/reports", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_FOUND.getCode()));
    }
}

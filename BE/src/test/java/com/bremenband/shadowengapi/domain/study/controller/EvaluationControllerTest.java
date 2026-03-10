package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.service.EvaluationService;
import com.bremenband.shadowengapi.domain.study.service.StudySessionService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudySessionController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class EvaluationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private StudySessionService studySessionService;
    @MockitoBean private EvaluationService   evaluationService;

    @Test
    @DisplayName("음성 파일과 sentenceId를 전송하면 평가 결과와 200을 반환한다")
    void sendVoice_성공_200() throws Exception {
        // given
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        Long userId     = 1L;

        EvaluationResponse response = new EvaluationResponse(
                sentenceId, 5.61, 10.78, 5.17,
                "I got it bad",
                new EvaluationResponse.Details(
                        List.of(new EvaluationResponse.WordLevelFeedback("I", "good")),
                        new EvaluationResponse.BoundaryToneFeedback("weak", "더 높게 말해보세요"),
                        new EvaluationResponse.DynamicStressFeedback("exaggerated", "강세를 줄여보세요")
                ),
                new EvaluationResponse.Scores(73.7, 93.8, 37.6, 73.0, 55.8, 76.0, 85.2, 100.0)
        );

        given(evaluationService.evaluate(eq(sessionId), eq(sentenceId), any(), any())).willReturn(response);

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "audio-bytes".getBytes());

        // when & then
        mockMvc.perform(multipart("/study-sessions/{sessionId}/evaluations", sessionId)
                        .file(audio)
                        .param("sentenceId", sentenceId.toString())
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.sentenceId").value(sentenceId))
                .andExpect(jsonPath("$.data.startSec").value(5.61))
                .andExpect(jsonPath("$.data.endSec").value(10.78))
                .andExpect(jsonPath("$.data.durationSec").value(5.17))
                .andExpect(jsonPath("$.data.userTranscription").value("I got it bad"))
                .andExpect(jsonPath("$.data.details.wordLevelFeedback[0].word").value("I"))
                .andExpect(jsonPath("$.data.details.wordLevelFeedback[0].status").value("good"))
                .andExpect(jsonPath("$.data.details.boundaryToneFeedback.status").value("weak"))
                .andExpect(jsonPath("$.data.details.boundaryToneFeedback.message").value("더 높게 말해보세요"))
                .andExpect(jsonPath("$.data.details.dynamicStressFeedback.status").value("exaggerated"))
                .andExpect(jsonPath("$.data.details.dynamicStressFeedback.message").value("강세를 줄여보세요"))
                .andExpect(jsonPath("$.data.scores.totalScore").value(73.7))
                .andExpect(jsonPath("$.data.scores.wordAccuracy").value(93.8));

        then(evaluationService).should(times(1)).evaluate(eq(sessionId), eq(sentenceId), any(), any());
    }

    @Test
    @DisplayName("세션이 없으면 404를 반환한다")
    void sendVoice_세션없음_404() throws Exception {
        // given
        Long sessionId  = 999L;
        Long sentenceId = 10L;

        given(evaluationService.evaluate(eq(sessionId), eq(sentenceId), any(), any()))
                .willThrow(new CustomException(ErrorCode.SESSION_NOT_FOUND));

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        // when & then
        mockMvc.perform(multipart("/study-sessions/{sessionId}/evaluations", sessionId)
                        .file(audio)
                        .param("sentenceId", sentenceId.toString())
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("음성 인식 실패 시 400을 반환한다")
    void sendVoice_음성인식실패_400() throws Exception {
        // given
        Long sessionId  = 1L;
        Long sentenceId = 10L;

        given(evaluationService.evaluate(eq(sessionId), eq(sentenceId), any(), any()))
                .willThrow(new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED));

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        // when & then
        mockMvc.perform(multipart("/study-sessions/{sessionId}/evaluations", sessionId)
                        .file(audio)
                        .param("sentenceId", sentenceId.toString())
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VOICE_RECOGNITION_FAILED.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VOICE_RECOGNITION_FAILED.getMessage()));
    }
}

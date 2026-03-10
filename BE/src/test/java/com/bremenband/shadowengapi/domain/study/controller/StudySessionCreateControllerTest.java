package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.study.service.EvaluationService;
import com.bremenband.shadowengapi.domain.study.service.StudySessionService;
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

@WebMvcTest(StudySessionController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class StudySessionCreateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private StudySessionService studySessionService;
    @MockitoBean private EvaluationService   evaluationService;

    private static final String EMBED_URL = "https://www.youtube.com/embed/dQw4w9WgXcQ";
    private static final String VIDEO_ID  = "dQw4w9WgXcQ";

    @Test
    @DisplayName("올바른 요청으로 세션 생성 시 sessionId, videoData, sentencesData를 포함한 200을 반환한다")
    void createStudySession_성공_200() throws Exception {
        // given
        Long userId = 1L;

        StudySessionCreateResponse.VideoData videoData = new StudySessionCreateResponse.VideoData(
                VIDEO_ID, EMBED_URL, "Never Gonna Give You Up",
                "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg",
                212L, "Rick Astley"
        );
        StudySessionCreateResponse.SentenceData sentenceData =
                new StudySessionCreateResponse.SentenceData(1234L, "Hello world", 15.5, 20.0, 4.5, 0);

        StudySessionCreateResponse response =
                new StudySessionCreateResponse(12345L, videoData, List.of(sentenceData));

        given(studySessionService.createStudySession(eq(userId), any())).willReturn(response);

        String body = objectMapper.writeValueAsString(Map.of(
                "embedUrl", EMBED_URL,
                "startSec", 15.5,
                "endSec", 45.0
        ));

        // when & then
        mockMvc.perform(post("/study-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(12345))
                .andExpect(jsonPath("$.data.videoData.videoId").value(VIDEO_ID))
                .andExpect(jsonPath("$.data.videoData.title").value("Never Gonna Give You Up"))
                .andExpect(jsonPath("$.data.videoData.duration").value(212))
                .andExpect(jsonPath("$.data.videoData.channelTitle").value("Rick Astley"))
                .andExpect(jsonPath("$.data.sentencesData[0].sentenceId").value(1234))
                .andExpect(jsonPath("$.data.sentencesData[0].sentence").value("Hello world"))
                .andExpect(jsonPath("$.data.sentencesData[0].startSec").value(15.5))
                .andExpect(jsonPath("$.data.sentencesData[0].studyCount").value(0));

        then(studySessionService).should(times(1)).createStudySession(eq(userId), any());
    }

    @Test
    @DisplayName("embedUrl이 빈 값이면 400을 반환한다")
    void createStudySession_embedUrl빈값_400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(Map.of(
                "embedUrl", "",
                "startSec", 15.5,
                "endSec", 45.0
        ));

        // when & then
        mockMvc.perform(post("/study-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 404를 반환한다")
    void createStudySession_사용자없음_404() throws Exception {
        // given
        Long userId = 999L;
        given(studySessionService.createStudySession(eq(userId), any()))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        String body = objectMapper.writeValueAsString(Map.of(
                "embedUrl", EMBED_URL,
                "startSec", 15.5,
                "endSec", 45.0
        ));

        // when & then
        mockMvc.perform(post("/study-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }
}

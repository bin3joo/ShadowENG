package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
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

@WebMvcTest(StudySessionController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class StudySessionDetailControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private StudySessionService studySessionService;
    @MockitoBean private EvaluationService   evaluationService;

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @Test
    @DisplayName("세션이 존재하면 videoData와 sentencesData를 포함한 200을 반환한다")
    void getStudySession_성공_200() throws Exception {
        // given
        Long sessionId = 1L;
        Long userId    = 1L;

        StudySessionCreateResponse.VideoData videoData = new StudySessionCreateResponse.VideoData(
                VIDEO_ID,
                "https://www.youtube.com/embed/" + VIDEO_ID,
                "Never Gonna Give You Up",
                "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg",
                212L,
                "Rick Astley"
        );
        List<StudySessionCreateResponse.SentenceData> sentencesData = List.of(
                new StudySessionCreateResponse.SentenceData(10L, "I got it bad.", 5.61, 10.78, 5.17, 2),
                new StudySessionCreateResponse.SentenceData(11L, "But what do I do?", 11.1, 15.83, 4.73, 0)
        );
        StudySessionCreateResponse response =
                new StudySessionCreateResponse(sessionId, videoData, sentencesData);

        given(studySessionService.getStudySession(eq(sessionId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.videoData.videoId").value(VIDEO_ID))
                .andExpect(jsonPath("$.data.videoData.title").value("Never Gonna Give You Up"))
                .andExpect(jsonPath("$.data.videoData.duration").value(212))
                .andExpect(jsonPath("$.data.videoData.channelTitle").value("Rick Astley"))
                .andExpect(jsonPath("$.data.sentencesData").isArray())
                .andExpect(jsonPath("$.data.sentencesData.length()").value(2))
                .andExpect(jsonPath("$.data.sentencesData[0].sentenceId").value(10))
                .andExpect(jsonPath("$.data.sentencesData[0].sentence").value("I got it bad."))
                .andExpect(jsonPath("$.data.sentencesData[0].startSec").value(5.61))
                .andExpect(jsonPath("$.data.sentencesData[0].studyCount").value(2))
                .andExpect(jsonPath("$.data.sentencesData[1].sentenceId").value(11))
                .andExpect(jsonPath("$.data.sentencesData[1].studyCount").value(0));

        then(studySessionService).should(times(1)).getStudySession(eq(sessionId), any());
    }

    @Test
    @DisplayName("문장이 없는 세션은 빈 sentencesData와 200을 반환한다")
    void getStudySession_문장없음_빈배열_200() throws Exception {
        // given
        Long sessionId = 2L;

        StudySessionCreateResponse.VideoData videoData = new StudySessionCreateResponse.VideoData(
                VIDEO_ID, "embed", "title", "thumb", 212L, "ch");
        StudySessionCreateResponse response =
                new StudySessionCreateResponse(sessionId, videoData, List.of());

        given(studySessionService.getStudySession(eq(sessionId), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentencesData").isArray())
                .andExpect(jsonPath("$.data.sentencesData.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 세션 ID면 404를 반환한다")
    void getStudySession_세션없음_404() throws Exception {
        // given
        Long sessionId = 999L;

        given(studySessionService.getStudySession(eq(sessionId), any()))
                .willThrow(new CustomException(ErrorCode.SESSION_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/study-sessions/{sessionId}", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SESSION_NOT_FOUND.getMessage()));

        then(studySessionService).should(times(1)).getStudySession(eq(sessionId), any());
    }
}

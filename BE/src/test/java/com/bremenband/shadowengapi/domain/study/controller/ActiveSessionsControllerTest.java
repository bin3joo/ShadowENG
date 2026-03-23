package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionsResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.res.ThumbnailInfo;
import com.bremenband.shadowengapi.domain.study.service.EvaluationService;
import com.bremenband.shadowengapi.domain.study.service.StudySessionService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
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
class ActiveSessionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private StudySessionService studySessionService;
    @MockitoBean private EvaluationService   evaluationService;

    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final String THUMBNAIL_URL =
            "https://i.ytimg.com/vi/" + VIDEO_ID + "/sddefault.jpg";

    @Test
    @DisplayName("ACTIVE 세션과 COMPLETED 세션이 있으면 각 목록에 담아 200을 반환한다")
    void getStudySessions_혼합세션_그룹핑200() throws Exception {
        // given
        Long userId = 1L;

        ActiveSessionsResponse response = new ActiveSessionsResponse(
                List.of(
                        new ActiveSessionResponse(12345L, "Test Video Title", new ThumbnailInfo(THUMBNAIL_URL, 640, 480), 8L, 5L),
                        new ActiveSessionResponse(67890L, "Test Video Title", new ThumbnailInfo(THUMBNAIL_URL, 640, 480), 6L, 2L)
                ),
                List.of(
                        new ActiveSessionResponse(11111L, "Test Video Title", new ThumbnailInfo(THUMBNAIL_URL, 640, 480), 10L, 10L)
                )
        );

        given(studySessionService.getAllSessions(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                // activeSessions 검증
                .andExpect(jsonPath("$.data.activeSessions").isArray())
                .andExpect(jsonPath("$.data.activeSessions.length()").value(2))
                .andExpect(jsonPath("$.data.activeSessions[0].sessionId").value(12345))
                .andExpect(jsonPath("$.data.activeSessions[0].totalSentences").value(8))
                .andExpect(jsonPath("$.data.activeSessions[0].completedSentences").value(5))
                .andExpect(jsonPath("$.data.activeSessions[0].thumbnails.url").value(THUMBNAIL_URL))
                .andExpect(jsonPath("$.data.activeSessions[0].thumbnails.width").value(640))
                .andExpect(jsonPath("$.data.activeSessions[0].thumbnails.height").value(480))
                .andExpect(jsonPath("$.data.activeSessions[1].sessionId").value(67890))
                // completedSessions 검증
                .andExpect(jsonPath("$.data.completedSessions").isArray())
                .andExpect(jsonPath("$.data.completedSessions.length()").value(1))
                .andExpect(jsonPath("$.data.completedSessions[0].sessionId").value(11111))
                .andExpect(jsonPath("$.data.completedSessions[0].totalSentences").value(10))
                .andExpect(jsonPath("$.data.completedSessions[0].completedSentences").value(10));

        then(studySessionService).should(times(1)).getAllSessions(userId);
    }

    @Test
    @DisplayName("세션이 없으면 activeSessions와 completedSessions 모두 빈 배열과 200을 반환한다")
    void getStudySessions_세션없음_둘다빈배열_200() throws Exception {
        // given
        Long userId = 1L;

        given(studySessionService.getAllSessions(userId))
                .willReturn(new ActiveSessionsResponse(List.of(), List.of()));

        // when & then
        mockMvc.perform(get("/study-sessions")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.activeSessions").isArray())
                .andExpect(jsonPath("$.data.activeSessions.length()").value(0))
                .andExpect(jsonPath("$.data.completedSessions").isArray())
                .andExpect(jsonPath("$.data.completedSessions.length()").value(0));

        then(studySessionService).should(times(1)).getAllSessions(userId);
    }
}

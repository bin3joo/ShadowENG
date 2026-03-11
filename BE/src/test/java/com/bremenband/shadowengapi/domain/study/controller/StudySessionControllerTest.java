package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.LatestActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
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
class StudySessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private StudySessionService studySessionService;
    @MockitoBean private EvaluationService   evaluationService;

    @Test
    @DisplayName("ACTIVE 세션이 존재하면 세션 정보, 썸네일, 영상 제목, 문장 진행 현황을 담은 200 응답을 반환한다")
    void getRecentSession_ACTIVE세션존재_200() throws Exception {
        // given
        Long userId  = 1L;
        String videoId = "dQw4w9WgXcQ";
        String base    = "https://i.ytimg.com/vi/" + videoId + "/";

        RecentStudySessionResponse response = new RecentStudySessionResponse(
                new LatestActiveSessionResponse(
                        12345L,
                        base + "sddefault.jpg",
                        "Never Gonna Give You Up",
                        8L,
                        5L
                )
        );

        given(studySessionService.getRecentSession(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/study-sessions/recent")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.latestActiveSession.sessionId").value(12345))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnailUrl").value(base + "sddefault.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.videoTitle").value("Never Gonna Give You Up"))
                .andExpect(jsonPath("$.data.latestActiveSession.totalSentences").value(8))
                .andExpect(jsonPath("$.data.latestActiveSession.completedSentences").value(5));

        then(studySessionService).should(times(1)).getRecentSession(userId);
    }

    @Test
    @DisplayName("ACTIVE 세션이 없으면 latestActiveSession이 null인 200 응답을 반환한다")
    void getRecentSession_ACTIVE세션없음_200_null() throws Exception {
        // given
        Long userId = 1L;

        given(studySessionService.getRecentSession(userId))
                .willReturn(new RecentStudySessionResponse(null));

        // when & then
        mockMvc.perform(get("/study-sessions/recent")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.latestActiveSession").doesNotExist());

        then(studySessionService).should(times(1)).getRecentSession(userId);
    }
}

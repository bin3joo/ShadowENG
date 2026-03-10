package com.bremenband.shadowengapi.domain.study.controller;

import com.bremenband.shadowengapi.domain.study.dto.res.LatestActiveSessionResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.res.ThumbnailInfo;
import com.bremenband.shadowengapi.domain.youtube.dto.res.ThumbnailsResponse;
import com.bremenband.shadowengapi.domain.study.service.StudySessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.bremenband.shadowengapi.domain.study.service.EvaluationService;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
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
    @DisplayName("ACTIVE 세션이 존재하면 세션 정보와 썸네일을 담은 200 응답을 반환한다")
    void getRecentSession_ACTIVE세션존재_200() throws Exception {
        // given
        Long userId = 1L;
        String videoId = "dQw4w9WgXcQ";
        String base = "https://i.ytimg.com/vi/" + videoId + "/";

        ThumbnailsResponse thumbnails = new ThumbnailsResponse(
                new ThumbnailInfo(base + "default.jpg", 120, 90),
                new ThumbnailInfo(base + "mqdefault.jpg", 320, 180),
                new ThumbnailInfo(base + "hqdefault.jpg", 480, 360),
                new ThumbnailInfo(base + "sddefault.jpg", 640, 480),
                new ThumbnailInfo(base + "maxresdefault.jpg", 1280, 720)
        );
        RecentStudySessionResponse response = new RecentStudySessionResponse(
                new LatestActiveSessionResponse(12345L, thumbnails, 40)
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
                .andExpect(jsonPath("$.data.latestActiveSession.progressRate").value(40))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.default.url")
                        .value(base + "default.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.default.width").value(120))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.default.height").value(90))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.medium.url")
                        .value(base + "mqdefault.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.high.url")
                        .value(base + "hqdefault.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.standard.url")
                        .value(base + "sddefault.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.maxres.url")
                        .value(base + "maxresdefault.jpg"))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.maxres.width").value(1280))
                .andExpect(jsonPath("$.data.latestActiveSession.thumbnails.maxres.height").value(720));

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

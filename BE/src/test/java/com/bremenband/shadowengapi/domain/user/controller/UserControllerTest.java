package com.bremenband.shadowengapi.domain.user.controller;

import com.bremenband.shadowengapi.domain.user.dto.res.UserDashboardResponse;
import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("유효한 Access Token으로 요청하면 사용자 정보와 200을 반환한다")
    void getUserData_성공() throws Exception {
        // given
        Long userId = 1L;
        List<LocalDate> studyDates = List.of(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 2),
                LocalDate.of(2025, 8, 3)
        );
        UserInfoResponse response = new UserInfoResponse(
                userId,
                "브레맨",
                "user@example.com",
                15,
                3,
                3,
                studyDates,
                LocalDateTime.of(2025, 8, 1, 10, 0, 0)
        );

        given(userService.getUserInfo(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("브레맨"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.totalVisitedDays").value(15))
                .andExpect(jsonPath("$.data.totalStudyDays").value(3))
                .andExpect(jsonPath("$.data.longestStreak").value(3))
                .andExpect(jsonPath("$.data.studyDates[0]").value("2025-08-01"))
                .andExpect(jsonPath("$.data.createdAt").value("2025-08-01T10:00:00"));

        then(userService).should(times(1)).getUserInfo(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 요청하면 404와 isSuccess=false를 반환한다")
    void getUserData_존재하지않는사용자_404() throws Exception {
        // given
        Long userId = 999L;

        given(userService.getUserInfo(userId))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());

        then(userService).should(times(1)).getUserInfo(userId);
    }

    @Test
    @DisplayName("대시보드 조회 시 통계와 레포트 날짜 목록을 담은 200 응답을 반환한다")
    void getDashboard_성공_200() throws Exception {
        // given
        Long userId = 1L;

        List<UserDashboardResponse.ReportInfo> reportsOnAug1 = List.of(
                new UserDashboardResponse.ReportInfo(10L, 5L, "https://thumb.jpg", "영어 공부 영상")
        );
        List<UserDashboardResponse.ReportDateEntry> reportDates = List.of(
                new UserDashboardResponse.ReportDateEntry(LocalDate.of(2025, 8, 1), reportsOnAug1)
        );
        UserDashboardResponse response = new UserDashboardResponse(
                5,
                10,
                42L,
                3600.0,
                List.of(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 3)),
                reportDates
        );

        given(userService.getDashboard(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/dashboard")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.longestStreak").value(5))
                .andExpect(jsonPath("$.data.totalAttendanceDays").value(10))
                .andExpect(jsonPath("$.data.totalStudiedSentences").value(42))
                .andExpect(jsonPath("$.data.totalStudyTimeSeconds").value(3600.0))
                .andExpect(jsonPath("$.data.studyDates[0]").value("2025-08-01"))
                .andExpect(jsonPath("$.data.studyDates[1]").value("2025-08-03"))
                .andExpect(jsonPath("$.data.reportDates[0].date").value("2025-08-01"))
                .andExpect(jsonPath("$.data.reportDates[0].reports[0].reportId").value(10))
                .andExpect(jsonPath("$.data.reportDates[0].reports[0].sessionId").value(5))
                .andExpect(jsonPath("$.data.reportDates[0].reports[0].videoTitle").value("영어 공부 영상"))
                .andExpect(jsonPath("$.data.reportDates[0].reports[0].thumbnailUrl").value("https://thumb.jpg"));

        then(userService).should(times(1)).getDashboard(userId);
    }

    @Test
    @DisplayName("학습 이력이 없는 신규 사용자는 빈 배열과 0 통계를 반환한다")
    void getDashboard_신규사용자_빈응답() throws Exception {
        // given
        Long userId = 2L;
        UserDashboardResponse response = new UserDashboardResponse(
                0, 0, 0L, 0.0, List.of(), List.of()
        );

        given(userService.getDashboard(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/dashboard")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.longestStreak").value(0))
                .andExpect(jsonPath("$.data.totalAttendanceDays").value(0))
                .andExpect(jsonPath("$.data.totalStudiedSentences").value(0))
                .andExpect(jsonPath("$.data.totalStudyTimeSeconds").value(0.0))
                .andExpect(jsonPath("$.data.studyDates").isEmpty())
                .andExpect(jsonPath("$.data.reportDates").isEmpty());

        then(userService).should(times(1)).getDashboard(userId);
    }
}

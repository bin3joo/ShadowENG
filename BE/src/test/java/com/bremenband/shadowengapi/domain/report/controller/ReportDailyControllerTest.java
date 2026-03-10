package com.bremenband.shadowengapi.domain.report.controller;

import com.bremenband.shadowengapi.domain.report.dto.res.DailyReportResponse;
import com.bremenband.shadowengapi.domain.report.service.ReportService;
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

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class ReportDailyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReportService reportService;

    @Test
    @DisplayName("데일리 레포트 조회 성공 시 날짜별 학습 데이터와 200을 반환한다")
    void getDailyReport_성공_200() throws Exception {
        // given
        Long userId = 1L;

        DailyReportResponse response = new DailyReportResponse(List.of(
                new DailyReportResponse.StudyDayData("2026-03-02", 15),
                new DailyReportResponse.StudyDayData("2026-03-03", 11)
        ));

        given(reportService.getDailyReport(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/reports/daily")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studyData").isArray())
                .andExpect(jsonPath("$.data.studyData.length()").value(2))
                .andExpect(jsonPath("$.data.studyData[0].date").value("2026-03-02"))
                .andExpect(jsonPath("$.data.studyData[0].studiedSentencesCount").value(15))
                .andExpect(jsonPath("$.data.studyData[1].date").value("2026-03-03"))
                .andExpect(jsonPath("$.data.studyData[1].studiedSentencesCount").value(11));

        then(reportService).should(times(1)).getDailyReport(userId);
    }

    @Test
    @DisplayName("학습 기록이 없으면 빈 배열과 200을 반환한다")
    void getDailyReport_학습기록없음_빈배열_200() throws Exception {
        // given
        Long userId = 2L;

        DailyReportResponse response = new DailyReportResponse(List.of());

        given(reportService.getDailyReport(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/reports/daily")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.studyData").isArray())
                .andExpect(jsonPath("$.data.studyData.length()").value(0));

        then(reportService).should(times(1)).getDailyReport(userId);
    }

}

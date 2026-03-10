package com.bremenband.shadowengapi.domain.report.service;

import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.entity.WeekSentence;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.report.repository.WeekSentenceRepository;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReportGetServiceTest {

    @InjectMocks private ReportService reportService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private ReportRepository       reportRepository;
    @Mock private WeekSentenceRepository weekSentenceRepository;

    private static final Long USER_ID = 1L;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private StudySession buildSession(Long sessionId) {
        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("T").embedUrl("e")
                .thumbnailUrl("th").duration(212).channelTitle("Ch").build();
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0).endSec(60).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Report buildReport(Long reportId, StudySession session) {
        Report report = Report.builder()
                .studySession(session)
                .totalScore(new BigDecimal("73.70"))
                .wordAccuracy(new BigDecimal("93.80"))
                .prosodyAndStress(new BigDecimal("37.60"))
                .wordRhythmScore(new BigDecimal("73.00"))
                .boundaryToneScore(new BigDecimal("55.80"))
                .dynamicStressScore(new BigDecimal("76.00"))
                .speedSimilarity(new BigDecimal("85.20"))
                .pauseSimilarity(new BigDecimal("100.00"))
                .build();
        ReflectionTestUtils.setField(report, "id", reportId);
        return report;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session, String content) {
        Sentence sentence = Sentence.builder()
                .studySession(session).content(content)
                .startSec(0).endSec(5).durationSec(5).build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("레포트가 존재하면 점수와 취약 문장 목록을 반환한다")
    void getReport_레포트존재_반환() {
        // given
        Long sessionId = 1L;
        Long reportId  = 100L;
        StudySession session = buildSession(sessionId);
        Report report        = buildReport(reportId, session);

        Sentence s1 = buildSentence(10L, session, "I got it bad.");
        WeekSentence ws = WeekSentence.builder().report(report).sentence(s1).build();
        ReflectionTestUtils.setField(ws, "id", 1L);

        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.of(report));
        given(weekSentenceRepository.findByReport_Id(reportId)).willReturn(List.of(ws));

        // when
        ReportResponse response = reportService.getReport(sessionId, USER_ID);

        // then
        assertThat(response.sessionId()).isEqualTo(sessionId);

        assertThat(response.scores().totalScore()).isEqualTo(73.70);
        assertThat(response.scores().wordAccuracy()).isEqualTo(93.80);
        assertThat(response.scores().prosodyAndStress()).isEqualTo(37.60);
        assertThat(response.scores().wordRhythmScore()).isEqualTo(73.00);
        assertThat(response.scores().boundaryToneScore()).isEqualTo(55.80);
        assertThat(response.scores().dynamicStressScore()).isEqualTo(76.00);
        assertThat(response.scores().speedSimilarity()).isEqualTo(85.20);
        assertThat(response.scores().pauseSimilarity()).isEqualTo(100.00);

        assertThat(response.difficultSentences()).hasSize(1);
        assertThat(response.difficultSentences().get(0).sentenceId()).isEqualTo(10L);
        assertThat(response.difficultSentences().get(0).sentence()).isEqualTo("I got it bad.");

        then(reportRepository).should(times(1)).findByStudySession_Id(sessionId);
        then(weekSentenceRepository).should(times(1)).findByReport_Id(reportId);
    }

    @Test
    @DisplayName("취약 문장이 없는 레포트도 빈 목록으로 반환한다")
    void getReport_취약문장없음_빈목록반환() {
        // given
        Long sessionId = 1L;
        Long reportId  = 100L;
        StudySession session = buildSession(sessionId);
        Report report        = buildReport(reportId, session);

        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.of(report));
        given(weekSentenceRepository.findByReport_Id(reportId)).willReturn(List.of());

        // when
        ReportResponse response = reportService.getReport(sessionId, USER_ID);

        // then
        assertThat(response.difficultSentences()).isEmpty();
    }

    @Test
    @DisplayName("레포트가 없으면 REPORT_NOT_FOUND 예외를 던진다")
    void getReport_레포트없음_예외() {
        // given
        Long sessionId = 999L;
        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reportService.getReport(sessionId, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);

        then(weekSentenceRepository).should(never()).findByReport_Id(any());
    }
}

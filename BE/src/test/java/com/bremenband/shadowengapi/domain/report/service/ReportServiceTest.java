package com.bremenband.shadowengapi.domain.report.service;

import com.bremenband.shadowengapi.domain.report.dto.res.ReportResponse;
import com.bremenband.shadowengapi.domain.report.entity.Report;
import com.bremenband.shadowengapi.domain.report.entity.WeekSentence;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.report.repository.WeekSentenceRepository;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
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
class ReportServiceTest {

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

    private Sentence buildSentence(Long sentenceId, StudySession session, String content) {
        Sentence sentence = Sentence.builder()
                .studySession(session).content(content)
                .startSec(0).endSec(5).durationSec(5).build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    private Evaluation buildEvaluation(Sentence sentence, StudySession session, double totalScore) {
        Evaluation eval = Evaluation.builder()
                .studySession(session).sentence(sentence)
                .userTranscription("test")
                .wordLevelFeedback("[]").boundaryToneFeedback("{}").dynamicStressFeedback("{}")
                .totalScore(BigDecimal.valueOf(totalScore))
                .wordAccuracy(BigDecimal.valueOf(90.0))
                .prosodyAndStress(BigDecimal.valueOf(80.0))
                .wordRhythmScore(BigDecimal.valueOf(75.0))
                .boundaryToneScore(BigDecimal.valueOf(85.0))
                .dynamicStressScore(BigDecimal.valueOf(70.0))
                .speedSimilarity(BigDecimal.valueOf(88.0))
                .pauseSimilarity(BigDecimal.valueOf(95.0))
                .build();
        return eval;
    }

    private Report buildReport(Long reportId, StudySession session) {
        Report report = Report.builder()
                .studySession(session)
                .totalScore(BigDecimal.valueOf(73.7))
                .wordAccuracy(BigDecimal.valueOf(90.0))
                .prosodyAndStress(BigDecimal.valueOf(80.0))
                .wordRhythmScore(BigDecimal.valueOf(75.0))
                .boundaryToneScore(BigDecimal.valueOf(85.0))
                .dynamicStressScore(BigDecimal.valueOf(70.0))
                .speedSimilarity(BigDecimal.valueOf(88.0))
                .pauseSimilarity(BigDecimal.valueOf(95.0))
                .build();
        ReflectionTestUtils.setField(report, "id", reportId);
        return report;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("평가 결과가 있으면 평균 점수를 계산하고 취약 문장을 포함한 레포트를 생성한다")
    void createReport_성공_취약문장포함() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);

        Sentence s1 = buildSentence(10L, session, "I got it bad.");   // 점수 낮음 → 취약
        Sentence s2 = buildSentence(11L, session, "Never give up.");  // 점수 높음

        Evaluation e1 = buildEvaluation(s1, session, 55.0); // < 70 → 취약
        Evaluation e2 = buildEvaluation(s2, session, 90.0); // >= 70 → 정상

        Report savedReport = buildReport(100L, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(evaluationRepository.findByStudySession_Id(sessionId)).willReturn(List.of(e1, e2));
        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.empty());
        given(reportRepository.save(any(Report.class))).willReturn(savedReport);

        WeekSentence ws = WeekSentence.builder().report(savedReport).sentence(s1).build();
        ReflectionTestUtils.setField(ws, "id", 1L);
        given(weekSentenceRepository.save(any(WeekSentence.class))).willReturn(ws);

        // when
        ReportResponse response = reportService.createReport(sessionId, USER_ID);

        // then
        assertThat(response.sessionId()).isEqualTo(sessionId);

        // 평균 totalScore = (55.0 + 90.0) / 2 = 72.5
        assertThat(response.scores().totalScore()).isEqualTo(72.5);
        assertThat(response.scores().wordAccuracy()).isEqualTo(90.0);

        // 취약 문장: s1 (avg 55.0 < 70)
        assertThat(response.difficultSentences()).hasSize(1);
        assertThat(response.difficultSentences().get(0).sentenceId()).isEqualTo(10L);
        assertThat(response.difficultSentences().get(0).sentence()).isEqualTo("I got it bad.");

        then(reportRepository).should(times(1)).save(any(Report.class));
        then(weekSentenceRepository).should(times(1)).save(any(WeekSentence.class));
    }

    @Test
    @DisplayName("모든 문장의 점수가 70 이상이면 취약 문장이 없는 레포트를 생성한다")
    void createReport_성공_취약문장없음() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);
        Sentence s1 = buildSentence(10L, session, "Good sentence.");

        Evaluation e1 = buildEvaluation(s1, session, 85.0);
        Report savedReport = buildReport(100L, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(evaluationRepository.findByStudySession_Id(sessionId)).willReturn(List.of(e1));
        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.empty());
        given(reportRepository.save(any(Report.class))).willReturn(savedReport);

        // when
        ReportResponse response = reportService.createReport(sessionId, USER_ID);

        // then
        assertThat(response.difficultSentences()).isEmpty();
        then(weekSentenceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("기존 레포트가 있으면 삭제 후 재생성한다")
    void createReport_기존레포트존재_재생성() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);
        Sentence s1 = buildSentence(10L, session, "sentence");
        Evaluation e1 = buildEvaluation(s1, session, 80.0);
        Report existingReport = buildReport(99L, session);
        Report savedReport    = buildReport(100L, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(evaluationRepository.findByStudySession_Id(sessionId)).willReturn(List.of(e1));
        given(reportRepository.findByStudySession_Id(sessionId)).willReturn(Optional.of(existingReport));
        given(reportRepository.save(any(Report.class))).willReturn(savedReport);

        // when
        reportService.createReport(sessionId, USER_ID);

        // then — 기존 레포트의 WeekSentence 삭제 후 레포트 삭제
        then(weekSentenceRepository).should(times(1)).deleteByReport_Id(99L);
        then(reportRepository).should(times(1)).delete(existingReport);
        then(reportRepository).should(times(1)).save(any(Report.class));
    }

    @Test
    @DisplayName("세션이 없으면 SESSION_NOT_FOUND 예외를 던진다")
    void createReport_세션없음_예외() {
        // given
        given(studySessionRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reportService.createReport(999L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);

        then(evaluationRepository).should(never()).findByStudySession_Id(any());
    }

    @Test
    @DisplayName("평가 결과가 없으면 NO_EVALUATIONS_FOR_REPORT 예외를 던진다")
    void createReport_평가없음_예외() {
        // given
        Long sessionId = 1L;
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId)));
        given(evaluationRepository.findByStudySession_Id(sessionId)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> reportService.createReport(sessionId, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NO_EVALUATIONS_FOR_REPORT);

        then(reportRepository).should(never()).save(any());
    }
}

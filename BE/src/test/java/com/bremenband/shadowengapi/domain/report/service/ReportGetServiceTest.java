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
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock private SentenceRepository     sentenceRepository;
    @Spy  private ObjectMapper           objectMapper;

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

    private Evaluation buildEvaluation(Sentence sentence, StudySession session, double totalScore) {
        Evaluation eval = Evaluation.builder()
                .studySession(session).sentence(sentence)
                .step(4)
                .userTranscription("test")
                .wordLevelFeedback("[{\"word\":\"I\",\"status\":\"good\"}]")
                .boundaryToneFeedback("{\"status\":\"weak\"}")
                .dynamicStressFeedback("{\"status\":\"monotone\"}")
                .totalScore(BigDecimal.valueOf(totalScore))
                .wordAccuracy(BigDecimal.valueOf(90.0))
                .prosodyAndStress(BigDecimal.valueOf(80.0))
                .wordRhythmScore(BigDecimal.valueOf(75.0))
                .boundaryToneScore(BigDecimal.valueOf(85.0))
                .dynamicStressScore(BigDecimal.valueOf(70.0))
                .speedSimilarity(BigDecimal.valueOf(88.0))
                .pauseSimilarity(BigDecimal.valueOf(95.0))
                .build();
        ReflectionTestUtils.setField(eval, "createdAt", LocalDateTime.now());
        return eval;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("레포트가 존재하면 점수와 취약 문장 목록(상태값 포함)을 반환한다")
    void getReport_레포트존재_반환() {
        // given
        Long sessionId = 1L;
        Long reportId  = 100L;
        StudySession session = buildSession(sessionId);
        Report report        = buildReport(reportId, session);

        Sentence s1 = buildSentence(10L, session, "I got it bad.");
        WeekSentence ws = WeekSentence.builder().report(report).sentence(s1).build();
        ReflectionTestUtils.setField(ws, "id", 1L);

        Evaluation e1 = buildEvaluation(s1, session, 55.0);

        given(reportRepository.findByIdAndStudySession_Id(reportId, sessionId)).willReturn(Optional.of(report));
        given(weekSentenceRepository.findByReport_Id(reportId)).willReturn(List.of(ws));
        given(evaluationRepository.findByStudySession_IdAndStep(sessionId, 4)).willReturn(List.of(e1));
        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(3L);

        // when
        ReportResponse response = reportService.getReport(sessionId, reportId, USER_ID);

        // then
        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.scores().totalScore()).isEqualTo(73.70);
        assertThat(response.scores().wordAccuracy()).isEqualTo(93.80);

        assertThat(response.difficultSentences()).hasSize(1);
        ReportResponse.DifficultSentence ds = response.difficultSentences().get(0);
        assertThat(ds.sentenceId()).isEqualTo(10L);
        assertThat(ds.sentence()).isEqualTo("I got it bad.");
        assertThat(ds.averageScore()).isEqualTo(55.0);
        assertThat(ds.boundaryToneStatus()).isEqualTo("weak");
        assertThat(ds.dynamicStressStatus()).isEqualTo("monotone");
        assertThat(ds.wordFeedback()).hasSize(1);
        assertThat(ds.wordFeedback().get(0).word()).isEqualTo("I");
        assertThat(ds.wordFeedback().get(0).status()).isEqualTo("good");

        then(reportRepository).should(times(1)).findByIdAndStudySession_Id(reportId, sessionId);
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

        given(reportRepository.findByIdAndStudySession_Id(reportId, sessionId)).willReturn(Optional.of(report));
        given(weekSentenceRepository.findByReport_Id(reportId)).willReturn(List.of());
        given(evaluationRepository.findByStudySession_IdAndStep(sessionId, 4)).willReturn(List.of());
        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(0L);

        // when
        ReportResponse response = reportService.getReport(sessionId, reportId, USER_ID);

        // then
        assertThat(response.difficultSentences()).isEmpty();
    }

    @Test
    @DisplayName("레포트가 없으면 REPORT_NOT_FOUND 예외를 던진다")
    void getReport_레포트없음_예외() {
        Long sessionId = 999L;
        Long reportId  = 1L;
        given(reportRepository.findByIdAndStudySession_Id(reportId, sessionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReport(sessionId, reportId, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);

        then(weekSentenceRepository).should(never()).findByReport_Id(any());
    }
}

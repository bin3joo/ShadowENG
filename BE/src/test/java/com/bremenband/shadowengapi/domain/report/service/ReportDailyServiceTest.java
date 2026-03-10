package com.bremenband.shadowengapi.domain.report.service;

import com.bremenband.shadowengapi.domain.report.dto.res.DailyReportResponse;
import com.bremenband.shadowengapi.domain.report.repository.ReportRepository;
import com.bremenband.shadowengapi.domain.report.repository.WeekSentenceRepository;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReportDailyServiceTest {

    @InjectMocks private ReportService reportService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private ReportRepository       reportRepository;
    @Mock private WeekSentenceRepository weekSentenceRepository;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private StudySession buildSession(Long sessionId, User user) {
        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("T").embedUrl("e")
                .thumbnailUrl("th").duration(212).channelTitle("Ch").build();
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0).endSec(60).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Evaluation buildEvaluation(StudySession session, Sentence sentence, LocalDateTime createdAt) {
        Evaluation evaluation = Evaluation.builder()
                .studySession(session)
                .sentence(sentence)
                .userTranscription("test")
                .wordLevelFeedback("[]")
                .boundaryToneFeedback("[]")
                .dynamicStressFeedback("[]")
                .totalScore(new BigDecimal("80.00"))
                .wordAccuracy(new BigDecimal("80.00"))
                .prosodyAndStress(new BigDecimal("80.00"))
                .wordRhythmScore(new BigDecimal("80.00"))
                .boundaryToneScore(new BigDecimal("80.00"))
                .dynamicStressScore(new BigDecimal("80.00"))
                .speedSimilarity(new BigDecimal("80.00"))
                .pauseSimilarity(new BigDecimal("80.00"))
                .build();
        ReflectionTestUtils.setField(evaluation, "createdAt", createdAt);
        return evaluation;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session) {
        Sentence sentence = Sentence.builder()
                .studySession(session).content("content")
                .startSec(0).endSec(5).durationSec(5).build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("여러 날짜에 평가가 있으면 날짜별 학습 문장 수를 반환한다")
    void getDailyReport_여러날짜_날짜별카운트반환() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p").build();
        ReflectionTestUtils.setField(user, "id", userId);

        StudySession session = buildSession(1L, user);
        Sentence s1 = buildSentence(1L, session);
        Sentence s2 = buildSentence(2L, session);
        Sentence s3 = buildSentence(3L, session);

        LocalDateTime day1 = LocalDateTime.of(2026, 3, 2, 10, 0);
        LocalDateTime day2 = LocalDateTime.of(2026, 3, 3, 11, 0);

        Evaluation e1 = buildEvaluation(session, s1, day1);
        Evaluation e2 = buildEvaluation(session, s2, day1);
        Evaluation e3 = buildEvaluation(session, s3, day2);

        given(evaluationRepository.findByStudySession_User_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of(e1, e2, e3));

        // when
        DailyReportResponse response = reportService.getDailyReport(userId);

        // then
        assertThat(response.studyData()).hasSize(2);
        assertThat(response.studyData().get(0).date()).isEqualTo("2026-03-02");
        assertThat(response.studyData().get(0).studiedSentencesCount()).isEqualTo(2);
        assertThat(response.studyData().get(1).date()).isEqualTo("2026-03-03");
        assertThat(response.studyData().get(1).studiedSentencesCount()).isEqualTo(1);

        then(evaluationRepository).should(times(1)).findByStudySession_User_IdOrderByCreatedAtAsc(userId);
    }

    @Test
    @DisplayName("평가 기록이 없으면 빈 목록을 반환한다")
    void getDailyReport_평가없음_빈목록반환() {
        // given
        Long userId = 1L;

        given(evaluationRepository.findByStudySession_User_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of());

        // when
        DailyReportResponse response = reportService.getDailyReport(userId);

        // then
        assertThat(response.studyData()).isEmpty();
    }

    @Test
    @DisplayName("같은 날짜의 평가는 하나의 항목으로 집계된다")
    void getDailyReport_같은날짜_단일항목() {
        // given
        Long userId = 2L;
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p").build();
        ReflectionTestUtils.setField(user, "id", userId);

        StudySession session = buildSession(2L, user);
        Sentence s1 = buildSentence(10L, session);
        Sentence s2 = buildSentence(11L, session);
        Sentence s3 = buildSentence(12L, session);

        LocalDateTime morning = LocalDateTime.of(2026, 3, 5, 9, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 3, 5, 20, 30);

        Evaluation e1 = buildEvaluation(session, s1, morning);
        Evaluation e2 = buildEvaluation(session, s2, morning);
        Evaluation e3 = buildEvaluation(session, s3, evening);

        given(evaluationRepository.findByStudySession_User_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of(e1, e2, e3));

        // when
        DailyReportResponse response = reportService.getDailyReport(userId);

        // then
        assertThat(response.studyData()).hasSize(1);
        assertThat(response.studyData().get(0).date()).isEqualTo("2026-03-05");
        assertThat(response.studyData().get(0).studiedSentencesCount()).isEqualTo(3);
    }
}

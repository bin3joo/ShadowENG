package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import com.bremenband.shadowengapi.domain.study.dto.res.SentenceLearningResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SentenceLearningServiceTest {

    @InjectMocks private StudySessionService studySessionService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private SentenceRepository     sentenceRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private PendingEvaluationStore pendingEvaluationStore;
    @Spy  private ObjectMapper           objectMapper = new ObjectMapper();

    private static final Long   SESSION_ID  = 1L;
    private static final Long   SENTENCE_ID = 10L;
    private static final Long   USER_ID     = 99L;
    private static final String CONTENT     =
            "Everybody knows you are a beautiful talented person.";

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private StudySession buildSession() {
        User user = User.builder()
                .email("test@test.com").nickname("tester")
                .provider("KAKAO").providerId("kakao-1").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("Test Video")
                .embedUrl("https://www.youtube.com/embed/dQw4w9WgXcQ")
                .thumbnailUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg")
                .duration(212).channelTitle("Test Channel").build();

        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0.0).endSec(60.0).build();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private Sentence buildSentence(StudySession session) {
        Sentence sentence = Sentence.builder()
                .studySession(session).content(CONTENT)
                .startSec(5.61).endSec(10.78).durationSec(5.17).build();
        ReflectionTestUtils.setField(sentence, "id", SENTENCE_ID);
        return sentence;
    }

    private void givenValidSessionAndSentence(int studyCount) {
        StudySession session = buildSession();
        Sentence sentence = buildSentence(session);
        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentence));
        given(evaluationRepository.countBySentence_Id(SENTENCE_ID)).willReturn((long) studyCount);
    }

    private Evaluation buildEvaluation(StudySession session, Sentence sentence, String wordLevelFeedback) {
        return Evaluation.builder()
                .studySession(session).sentence(sentence)
                .userTranscription("test transcription")
                .wordLevelFeedback(wordLevelFeedback)
                .boundaryToneFeedback("{\"status\":\"good\"}")
                .dynamicStressFeedback("{\"status\":\"good\"}")
                .totalScore(BigDecimal.valueOf(80.0)).wordAccuracy(BigDecimal.valueOf(80.0))
                .prosodyAndStress(BigDecimal.valueOf(80.0)).wordRhythmScore(BigDecimal.valueOf(80.0))
                .boundaryToneScore(BigDecimal.valueOf(80.0)).dynamicStressScore(BigDecimal.valueOf(80.0))
                .speedSimilarity(BigDecimal.valueOf(80.0)).pauseSimilarity(BigDecimal.valueOf(80.0))
                .build();
    }

    private PendingEvaluation buildPendingStep2(String wordLevelFeedback) {
        return new PendingEvaluation(
                2, "transcription", wordLevelFeedback,
                "{\"status\":\"good\"}", "{\"status\":\"good\"}",
                80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
    }

    // ── step별 응답 검증 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("step 1: sentence와 hiddenSentence 모두 null을 반환한다 (청취/쉐도잉 단계)")
    void getSentenceLearning_step1_모두null() {
        givenValidSessionAndSentence(0);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, USER_ID);

        assertThat(response.step()).isEqualTo(1);
        assertThat(response.sentence()).isNull();
        assertThat(response.hiddenSentence()).isNull();
    }

    @Test
    @DisplayName("step 2: sentence만 반환하고 hiddenSentence는 null이다 (원문 읽기 단계)")
    void getSentenceLearning_step2_원문만반환() {
        givenValidSessionAndSentence(1);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 2, USER_ID);

        assertThat(response.step()).isEqualTo(2);
        assertThat(response.sentence()).isEqualTo(CONTENT);
        assertThat(response.hiddenSentence()).isNull();
    }

    @Test
    @DisplayName("step 3: sentence와 hiddenSentence 모두 반환한다 (원문+빈칸 단계)")
    void getSentenceLearning_step3_원문과마스킹모두반환() {
        givenValidSessionAndSentence(2);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        assertThat(response.step()).isEqualTo(3);
        assertThat(response.sentence()).isEqualTo(CONTENT);
        assertThat(response.hiddenSentence()).isNotNull();
        assertThat(response.hiddenSentence()).contains("_____");
    }

    @Test
    @DisplayName("step 4: sentence와 hiddenSentence 모두 null이다 (step 1과 동일)")
    void getSentenceLearning_step4_step1과동일_모두null() {
        givenValidSessionAndSentence(3);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 4, USER_ID);

        assertThat(response.step()).isEqualTo(4);
        assertThat(response.sentence()).isNull();
        assertThat(response.hiddenSentence()).isNull();
    }

    @Test
    @DisplayName("step 3: hiddenSentence는 원문과 다르고 _____ 를 포함한다")
    void getSentenceLearning_step3_hiddenSentence_마스킹됨() {
        givenValidSessionAndSentence(1);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        assertThat(response.hiddenSentence()).isNotEqualTo(CONTENT);
        assertThat(response.hiddenSentence()).contains("_____");
    }

    @Test
    @DisplayName("공통: studyCount, startSec, endSec, durationSec는 step과 무관하게 항상 반환한다")
    void getSentenceLearning_메타데이터_항상반환() {
        givenValidSessionAndSentence(5);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, USER_ID);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.sentenceId()).isEqualTo(SENTENCE_ID);
        assertThat(response.studyCount()).isEqualTo(5);
        assertThat(response.startSec()).isEqualTo(5.61);
        assertThat(response.endSec()).isEqualTo(10.78);
        assertThat(response.durationSec()).isEqualTo(5.17);
    }

    // ── 예외 케이스 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 세션 ID면 SESSION_NOT_FOUND 예외를 던진다")
    void getSentenceLearning_세션없음_예외() {
        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("세션 소유자가 아니면 FORBIDDEN 예외를 던진다")
    void getSentenceLearning_소유권위반_예외() {
        StudySession session = buildSession();
        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        assertThatThrownBy(() ->
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, 999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 문장 ID면 SENTENCE_NOT_FOUND 예외를 던진다")
    void getSentenceLearning_문장없음_예외() {
        StudySession session = buildSession();
        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SENTENCE_NOT_FOUND);
    }

    @Test
    @DisplayName("문장이 해당 세션 소속이 아니면 INVALID_REQUEST 예외를 던진다")
    void getSentenceLearning_다른세션문장_예외() {
        StudySession session = buildSession();

        StudySession otherSession = StudySession.builder()
                .video(session.getVideo()).user(session.getUser())
                .startSec(0.0).endSec(30.0).build();
        ReflectionTestUtils.setField(otherSession, "id", 999L);

        Sentence sentenceOfOther = Sentence.builder()
                .studySession(otherSession).content(CONTENT)
                .startSec(0.0).endSec(5.0).durationSec(5.0).build();
        ReflectionTestUtils.setField(sentenceOfOther, "id", SENTENCE_ID);

        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentenceOfOther));

        assertThatThrownBy(() ->
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 1, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ── step 3 평가 기반 마스킹 (DB 경로) ────────────────────────────────────

    @Test
    @DisplayName("step 3: Redis miss 시 DB의 최신 평가에서 good이 아닌 단어(rushed)를 마스킹한다")
    void getSentenceLearning_step3_DB_rushed단어_마스킹됨() {
        // given — Redis miss (pendingEvaluationStore.findStep returns Optional.empty() by default)
        StudySession session = buildSession();
        Sentence sentence = buildSentence(session);
        String feedback = "[{\"word\":\"beautiful\",\"status\":\"rushed\"}," +
                          "{\"word\":\"talented\",\"status\":\"good\"}]";
        Evaluation evaluation = buildEvaluation(session, sentence, feedback);

        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentence));
        given(evaluationRepository.countBySentence_Id(SENTENCE_ID)).willReturn(2L);
        given(evaluationRepository.findTopBySentence_IdOrderByCreatedAtDesc(SENTENCE_ID))
                .willReturn(Optional.of(evaluation));

        // when
        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        // then
        assertThat(response.hiddenSentence()).contains("_____");
        assertThat(response.hiddenSentence()).doesNotContain("beautiful");
        assertThat(response.hiddenSentence()).contains("talented");
    }

    @Test
    @DisplayName("step 3: Redis miss 시 DB의 최신 평가에서 missed 단어를 마스킹한다")
    void getSentenceLearning_step3_DB_missed단어_마스킹됨() {
        // given
        StudySession session = buildSession();
        Sentence sentence = buildSentence(session);
        String feedback = "[{\"word\":\"talented\",\"status\":\"missed\"}]";
        Evaluation evaluation = buildEvaluation(session, sentence, feedback);

        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentence));
        given(evaluationRepository.countBySentence_Id(SENTENCE_ID)).willReturn(1L);
        given(evaluationRepository.findTopBySentence_IdOrderByCreatedAtDesc(SENTENCE_ID))
                .willReturn(Optional.of(evaluation));

        // when
        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        // then
        assertThat(response.hiddenSentence()).doesNotContain("talented");
        assertThat(response.hiddenSentence()).contains("_____");
    }

    @Test
    @DisplayName("step 3: DB 최신 평가에서 모든 단어가 good이면 마스킹 없이 원문을 반환한다")
    void getSentenceLearning_step3_DB_모두good_마스킹없음() {
        // given
        StudySession session = buildSession();
        Sentence sentence = buildSentence(session);
        String feedback = "[{\"word\":\"beautiful\",\"status\":\"good\"}," +
                          "{\"word\":\"talented\",\"status\":\"good\"}]";
        Evaluation evaluation = buildEvaluation(session, sentence, feedback);

        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentence));
        given(evaluationRepository.countBySentence_Id(SENTENCE_ID)).willReturn(1L);
        given(evaluationRepository.findTopBySentence_IdOrderByCreatedAtDesc(SENTENCE_ID))
                .willReturn(Optional.of(evaluation));

        // when
        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        // then
        assertThat(response.hiddenSentence()).isEqualTo(CONTENT);
        assertThat(response.hiddenSentence()).doesNotContain("_____");
    }

    @Test
    @DisplayName("step 3: Redis도 DB도 없으면 랜덤 마스킹으로 폴백하여 _____ 를 포함한다")
    void getSentenceLearning_step3_평가없음_랜덤마스킹폴백() {
        givenValidSessionAndSentence(0);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        assertThat(response.hiddenSentence()).isNotNull();
        assertThat(response.hiddenSentence()).contains("_____");
    }

    // ── step 3 평가 기반 마스킹 (Redis 경로) ──────────────────────────────────

    @Test
    @DisplayName("step 3: Redis에 step-2 평가가 있으면 DB 조회 없이 Redis 피드백으로 마스킹한다")
    void getSentenceLearning_step3_Redis_step2평가_Redis피드백으로마스킹() {
        // given
        StudySession session = buildSession();
        Sentence sentence = buildSentence(session);
        String feedback = "[{\"word\":\"beautiful\",\"status\":\"rushed\"}," +
                          "{\"word\":\"talented\",\"status\":\"good\"}]";

        given(studySessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(SENTENCE_ID)).willReturn(Optional.of(sentence));
        given(evaluationRepository.countBySentence_Id(SENTENCE_ID)).willReturn(0L);
        given(pendingEvaluationStore.findStep(SESSION_ID, SENTENCE_ID, 2))
                .willReturn(Optional.of(buildPendingStep2(feedback)));

        // when
        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, 3, USER_ID);

        // then
        assertThat(response.hiddenSentence()).contains("_____");
        assertThat(response.hiddenSentence()).doesNotContain("beautiful");
        assertThat(response.hiddenSentence()).contains("talented");
        then(evaluationRepository).should(never()).findTopBySentence_IdOrderByCreatedAtDesc(any());
    }

    // ── step 1 / step 4 동등성 검증 ──────────────────────────────────────────

    @ParameterizedTest(name = "step {0}: sentence=null, hiddenSentence=null")
    @ValueSource(ints = {1, 4})
    @DisplayName("step 1과 step 4는 모두 텍스트를 반환하지 않는다")
    void getSentenceLearning_step1과step4_동일하게null(int step) {
        givenValidSessionAndSentence(0);

        SentenceLearningResponse response =
                studySessionService.getSentenceLearning(SESSION_ID, SENTENCE_ID, step, USER_ID);

        assertThat(response.sentence()).isNull();
        assertThat(response.hiddenSentence()).isNull();
    }
}

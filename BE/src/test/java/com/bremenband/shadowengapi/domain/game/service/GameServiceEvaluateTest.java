package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.dto.redis.GameSessionData;
import com.bremenband.shadowengapi.domain.game.dto.res.GameEvaluationResponse;
import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameService.evaluate() - 라운드 음성 평가")
class GameServiceEvaluateTest {

    @InjectMocks private GameService gameService;

    @Mock private UserGameProfileRepository   userGameProfileRepository;
    @Mock private DailyGameSentenceRepository dailyGameSentenceRepository;
    @Mock private DailyBestRecordRepository   dailyBestRecordRepository;
    @Mock private PythonApiClient             pythonApiClient;
    @Mock private S3Uploader                  s3Uploader;
    @Mock private GameSessionStore            gameSessionStore;
    @Mock private GameWriter                  gameWriter;
    @Spy  private ObjectMapper                objectMapper;

    private static final Long   USER_ID       = 1L;
    private static final int    LEVEL         = 1;
    private static final String FEATURES_JSON = "{\"f0_array\":[120.1],\"rms_array\":[0.03]}";

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private DailyGameSentence buildDailySentence(int level) {
        GameSentence gs = GameSentence.builder()
                .level(level)
                .content("I got it bad.")
                .featuresUrl("features/game-test.json")
                .wordTimestamps("[{\"word\":\"I\",\"start\":0.0,\"end\":0.5}]")
                .referenceAudioUrl("https://s3.example.com/ref.mp3")
                .build();
        ReflectionTestUtils.setField(gs, "id", 1L);

        DailyGameSentence daily = DailyGameSentence.builder()
                .level(level).gameSentence(gs).assignedDate(LocalDate.now()).build();
        ReflectionTestUtils.setField(daily, "id", 1L);
        return daily;
    }

    private DailyBestRecord buildBestRecord(int level, int hearts) {
        User user = User.builder().email("u@e.com").nickname("nick").provider("KAKAO").providerId("p1").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        GameRecord record = GameRecord.builder()
                .user(user).level(level).playedDate(LocalDate.now())
                .hearts(hearts)
                .avgTotalScore(BigDecimal.valueOf(80.0)).avgSpeedSimilarity(BigDecimal.valueOf(80.0))
                .avgDynamicStressScore(BigDecimal.valueOf(70.0)).avgBoundaryToneScore(BigDecimal.valueOf(75.0))
                .finalScore(BigDecimal.valueOf(88.0)).cumulativeScore(BigDecimal.valueOf(80.0))
                .build();
        ReflectionTestUtils.setField(record, "id", 10L);
        return DailyBestRecord.builder()
                .userId(USER_ID).level(level).recordDate(LocalDate.now())
                .gameRecord(record).bestFinalScore(BigDecimal.valueOf(88.0)).build();
    }

    private PythonEvaluateAudioResponse buildPythonResponse(double totalScore) {
        List<PythonEvaluateAudioResponse.WordLevelFeedback> wlf = List.of(
                new PythonEvaluateAudioResponse.WordLevelFeedback("I", "good", 0.0, 0.5, 0.0, 0.5));
        PythonEvaluateAudioResponse.BoundaryToneFeedback btf =
                new PythonEvaluateAudioResponse.BoundaryToneFeedback(0.5, 0.4, "good");
        PythonEvaluateAudioResponse.DynamicStressFeedback dsf =
                new PythonEvaluateAudioResponse.DynamicStressFeedback(0.3, 0.3, "good");
        PythonEvaluateAudioResponse.Details details =
                new PythonEvaluateAudioResponse.Details(wlf, btf, dsf, List.of());
        PythonEvaluateAudioResponse.Scores scores =
                new PythonEvaluateAudioResponse.Scores(totalScore, 90.0, 40.0, 75.0, 60.0, 78.0, 85.0, 100.0);
        return new PythonEvaluateAudioResponse("SUCCESS", null, "I got it bad", details, scores);
    }

    private GameSessionData buildSession(int completedRound, int hearts) {
        GameSessionData s = new GameSessionData();
        s.setHearts(hearts);
        for (int r = 1; r <= completedRound; r++) {
            s.addRound(new GameSessionData.RoundResult(r, "[{\"word\":\"I\",\"status\":\"good\"}]",
                    80.0, 85.0, 75.0, 70.0));
        }
        return s;
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "test.wav", "audio/wav", "bytes".getBytes());
    }

    // ── 레벨 해금 / 문장 검증 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("레벨 2가 잠겨 있으면 GAME_LEVEL_LOCKED 예외가 발생한다")
    void evaluate_레벨잠김_예외() {
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(eq(USER_ID), eq(1), any(LocalDate.class)))
                .willReturn(Optional.empty()); // level1 기록 없음 → level2 잠김

        assertThatThrownBy(() -> gameService.evaluate(USER_ID, 2, 1, audioFile()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_LEVEL_LOCKED);

        then(pythonApiClient).should(never()).evaluateAudio(any());
    }

    @Test
    @DisplayName("오늘의 문장이 없으면 GAME_DAILY_SENTENCE_NOT_ASSIGNED 예외가 발생한다")
    void evaluate_오늘문장없음_예외() {
        // level 1 is always unlocked
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.evaluate(USER_ID, LEVEL, 1, audioFile()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_DAILY_SENTENCE_NOT_ASSIGNED);
    }

    // ── Round 순서 검증 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("round 2 제출 시 Redis에 round 1 결과가 없으면 GAME_ROUND_INVALID 예외가 발생한다")
    void evaluate_round2_이전라운드없음_예외() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(gameSessionStore.find(eq(USER_ID), eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.evaluate(USER_ID, LEVEL, 2, audioFile()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_ROUND_INVALID);
    }

    @Test
    @DisplayName("round 2 제출 시 세션이 gameOver 상태이면 GAME_ROUND_INVALID 예외가 발생한다")
    void evaluate_round2_gameOver세션_예외() {
        GameSessionData session = buildSession(1, 0);
        session.setGameOver(true);

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(gameSessionStore.find(eq(USER_ID), eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.evaluate(USER_ID, LEVEL, 2, audioFile()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_ROUND_INVALID);
    }

    // ── Python FAIL ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Python API가 FAIL을 반환하면 VOICE_RECOGNITION_FAILED 예외가 발생한다")
    void evaluate_Python_FAIL_예외() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any(PythonEvaluateAudioRequest.class)))
                .willReturn(new PythonEvaluateAudioResponse("FAIL", "fail", null, null, null));

        assertThatThrownBy(() -> gameService.evaluate(USER_ID, LEVEL, 1, audioFile()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VOICE_RECOGNITION_FAILED);

        then(s3Uploader).should(never()).upload(any());
        then(s3Uploader).should(never()).delete(any());
        then(gameWriter).should(never()).saveGameResult(any(), anyInt(), any(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    // ── 정상 플로우 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("round 1에서 70점 이상이면 hearts=1, gameOver=false를 반환한다")
    void evaluate_round1_합격_gameOver아님() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(75.0));

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 1, audioFile());

        assertThat(response.hearts()).isEqualTo(1);
        assertThat(response.gameOver()).isFalse();
        assertThat(response.result()).isNull();
        assertThat(response.round()).isEqualTo(1);

        // 세션 저장 O, DB 저장 X
        then(gameSessionStore).should(times(1)).save(eq(USER_ID), eq(LEVEL), any(LocalDate.class), any());
        then(gameWriter).should(never()).saveGameResult(any(), anyInt(), any(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("round 1에서 보정 후 점수(65.0)가 HEART_THRESHOLD(60.0) 이상이면 합격하고 gameOver가 발생하지 않는다")
    void evaluate_round1_보정후합격_gameOver없음() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(50.0)); // 50.0 + 15.0 = 65.0

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 1, audioFile());

        // 50.0 + 15.0 = 65.0 >= HEART_THRESHOLD(60.0) → 합격
        assertThat(response.hearts()).isEqualTo(1);
        assertThat(response.gameOver()).isFalse();
        assertThat(response.result()).isNull();

        // round 1이 gameOver가 아니므로 DB 저장 없음
        then(gameWriter).should(never()).saveGameResult(any(), anyInt(), any(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("round 2에서 합격하면 hearts=2, gameOver=false")
    void evaluate_round2_합격() {
        GameSessionData existingSession = buildSession(1, 1); // round 1 완료, hearts=1

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(gameSessionStore.find(eq(USER_ID), eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(existingSession));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(80.0));

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 2, audioFile());

        assertThat(response.hearts()).isEqualTo(2);
        assertThat(response.gameOver()).isFalse();
        assertThat(response.result()).isNull();
    }

    @Test
    @DisplayName("round 3에서 합격하면 hearts=3, gameOver=true, 최종 결과 포함")
    void evaluate_round3_합격_게임종료() {
        GameSessionData existingSession = buildSession(2, 2); // round 1,2 완료, hearts=2

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(gameSessionStore.find(eq(USER_ID), eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(existingSession));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(85.0));

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 3, audioFile());

        assertThat(response.hearts()).isEqualTo(3);
        assertThat(response.gameOver()).isTrue();
        assertThat(response.result()).isNotNull();
        assertThat(response.result().hearts()).isEqualTo(3);

        then(gameWriter).should(times(1)).saveGameResult(eq(USER_ID), eq(LEVEL), any(LocalDate.class),
                eq(3), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        then(gameSessionStore).should(times(1)).delete(eq(USER_ID), eq(LEVEL), any(LocalDate.class));
    }

    @Test
    @DisplayName("round 1 재도전: 이전 세션을 무시하고 새 세션으로 시작한다")
    void evaluate_round1_재도전_세션초기화() {
        // 이전 gameOver 세션이 있어도 round 1은 새로 시작
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(75.0));

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 1, audioFile());

        // gameSessionStore.find() 는 round 1에서 호출되지 않음
        then(gameSessionStore).should(never()).find(any(), anyInt(), any(LocalDate.class));
        assertThat(response.round()).isEqualTo(1);
    }

    @Test
    @DisplayName("게임 종료(round 3) 시 final_score = avgTotal × (1 + hearts × 0.1)")
    void evaluate_finalScore_가중치계산() {
        // round 1,2 완료 (hearts=2), round 3에서 합격 → hearts=3
        // finalScore = avgTotal × (1 + 3 × 0.1) = avgTotal × 1.3
        GameSessionData existingSession = buildSession(2, 2);

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(gameSessionStore.find(eq(USER_ID), eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(existingSession));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(65.0));

        GameEvaluationResponse response = gameService.evaluate(USER_ID, LEVEL, 3, audioFile());

        // hearts=3 (모든 라운드 합격), finalScore > avgTotalScore
        assertThat(response.result()).isNotNull();
        assertThat(response.result().hearts()).isEqualTo(3);
        assertThat(response.result().finalScore()).isGreaterThan(response.result().avgTotalScore());
    }

    @Test
    @DisplayName("음성 파일은 S3에 업로드하지 않고 Base64로 인코딩하여 Python API에 전달한다")
    void evaluate_audioEncodedAsBase64_S3업로드없음() {
        byte[] audioBytes = "game-audio-content".getBytes();
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.webm", "audio/webm", audioBytes);

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(eq(LEVEL), any(LocalDate.class)))
                .willReturn(Optional.of(buildDailySentence(LEVEL)));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildPythonResponse(80.0));

        gameService.evaluate(USER_ID, LEVEL, 1, audio);

        // S3 업로드/삭제 없음
        then(s3Uploader).should(never()).upload(any());
        then(s3Uploader).should(never()).delete(any());

        // Python에 Base64 인코딩된 바이트가 전달되었는지 검증
        ArgumentCaptor<PythonEvaluateAudioRequest> captor =
                ArgumentCaptor.forClass(PythonEvaluateAudioRequest.class);
        then(pythonApiClient).should(times(1)).evaluateAudio(captor.capture());
        PythonEvaluateAudioRequest captured = captor.getValue();

        byte[] decoded = Base64.getDecoder().decode(captured.userAudio());
        assertThat(decoded).isEqualTo(audioBytes);
        assertThat(captured.userAudioFormat()).isEqualTo("webm");
    }
}

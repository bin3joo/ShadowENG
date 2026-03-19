package com.bremenband.shadowengapi.domain.game.controller;

import com.bremenband.shadowengapi.domain.game.dto.res.*;
import com.bremenband.shadowengapi.domain.game.service.GameService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@Import({SecurityConfig.class, JwtProvider.class})
@DisplayName("GameController - Web Layer 테스트")
class GameControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameService gameService;

    private static final Long USER_ID = 1L;
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());

    // ── GET /game/today ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /game/today: 레벨별 해금 상태와 오늘 기록을 200으로 반환한다")
    void getToday_200() throws Exception {
        GameTodayResponse response = new GameTodayResponse(List.of(
                new GameTodayResponse.LevelStatus(1, true,
                        new GameTodayResponse.DailyBest(3, 99.0)),
                new GameTodayResponse.LevelStatus(2, true, null),
                new GameTodayResponse.LevelStatus(3, false, null)
        ));
        given(gameService.getToday(USER_ID)).willReturn(response);

        mockMvc.perform(get("/game/today")
                        .with(authentication(AUTH))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.levels[0].level").value(1))
                .andExpect(jsonPath("$.data.levels[0].unlocked").value(true))
                .andExpect(jsonPath("$.data.levels[0].todayBest.hearts").value(3))
                .andExpect(jsonPath("$.data.levels[0].todayBest.finalScore").value(99.0))
                .andExpect(jsonPath("$.data.levels[1].todayBest").isEmpty())
                .andExpect(jsonPath("$.data.levels[2].unlocked").value(false));

        then(gameService).should(times(1)).getToday(USER_ID);
    }

    // ── GET /game/levels/{level}/rounds/{round} ──────────────────────────────────

    @Test
    @DisplayName("GET /game/levels/1/rounds/1: round 1은 전체 문장과 오디오 URL을 200으로 반환한다")
    void getRoundSentence_round1_200() throws Exception {
        GameRoundResponse response = new GameRoundResponse(1, 1,
                "I got it bad.", "I ___ it bad.", "https://s3.example.com/ref.mp3");
        given(gameService.getRoundSentence(USER_ID, 1, 1)).willReturn(response);

        mockMvc.perform(get("/game/levels/1/rounds/1")
                        .with(authentication(AUTH)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.round").value(1))
                .andExpect(jsonPath("$.data.sentence").value("I got it bad."))
                .andExpect(jsonPath("$.data.maskedSentence").value("I ___ it bad."))
                .andExpect(jsonPath("$.data.referenceAudioUrl").value("https://s3.example.com/ref.mp3"));
    }

    @Test
    @DisplayName("GET /game/levels/1/rounds/3: round 3은 sentence가 null이다")
    void getRoundSentence_round3_sentence_null() throws Exception {
        GameRoundResponse response = new GameRoundResponse(1, 3, null, "I ___ it bad.", "https://s3.example.com/ref.mp3");
        given(gameService.getRoundSentence(USER_ID, 1, 3)).willReturn(response);

        mockMvc.perform(get("/game/levels/1/rounds/3")
                        .with(authentication(AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentence").doesNotExist());
    }

    @Test
    @DisplayName("GET /game/levels/2/rounds/1: 레벨 잠김이면 403을 반환한다")
    void getRoundSentence_레벨잠김_403() throws Exception {
        given(gameService.getRoundSentence(USER_ID, 2, 1))
                .willThrow(new CustomException(ErrorCode.GAME_LEVEL_LOCKED));

        mockMvc.perform(get("/game/levels/2/rounds/1")
                        .with(authentication(AUTH)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.GAME_LEVEL_LOCKED.getCode()));
    }

    @Test
    @DisplayName("GET /game/levels/1/rounds/1: 오늘의 문장 미배정이면 503을 반환한다")
    void getRoundSentence_문장미배정_503() throws Exception {
        given(gameService.getRoundSentence(USER_ID, 1, 1))
                .willThrow(new CustomException(ErrorCode.GAME_DAILY_SENTENCE_NOT_ASSIGNED));

        mockMvc.perform(get("/game/levels/1/rounds/1")
                        .with(authentication(AUTH)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.GAME_DAILY_SENTENCE_NOT_ASSIGNED.getCode()));
    }

    // ── POST /game/levels/{level}/rounds/{round}/evaluate ────────────────────────

    @Test
    @DisplayName("POST evaluate: 합격(gameOver=false)이면 결과 없이 200 반환")
    void evaluate_합격_게임진행중_200() throws Exception {
        GameEvaluationResponse response = new GameEvaluationResponse(
                "I got it bad",
                new GameEvaluationResponse.Details(
                        List.of(new GameEvaluationResponse.Details.WordLevelFeedback("I", "good")),
                        new GameEvaluationResponse.Details.BoundaryToneFeedback("good"),
                        new GameEvaluationResponse.Details.DynamicStressFeedback("good")
                ),
                new GameEvaluationResponse.Scores(80.0, 90.0, 40.0, 75.0, 65.0, 78.0, 85.0, 100.0),
                1, 1, false, null
        );
        given(gameService.evaluate(eq(USER_ID), eq(1), eq(1), any())).willReturn(response);

        MockMultipartFile audio = new MockMultipartFile("file", "test.wav", "audio/wav", "bytes".getBytes());

        mockMvc.perform(multipart("/game/levels/1/rounds/1/evaluate")
                        .file(audio)
                        .with(authentication(AUTH)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.hearts").value(1))
                .andExpect(jsonPath("$.data.gameOver").value(false))
                .andExpect(jsonPath("$.data.result").doesNotExist())
                .andExpect(jsonPath("$.data.scores.totalScore").value(80.0));

        then(gameService).should(times(1)).evaluate(eq(USER_ID), eq(1), eq(1), any());
    }

    @Test
    @DisplayName("POST evaluate: 불합격(gameOver=true)이면 GameResult를 포함해 200 반환")
    void evaluate_불합격_게임종료_200() throws Exception {
        GameEvaluationResponse.GameResult result = new GameEvaluationResponse.GameResult(
                65.0, 80.0, 60.0, 70.0, 65.0, 65.0, 0);

        GameEvaluationResponse response = new GameEvaluationResponse(
                "I got it bad",
                new GameEvaluationResponse.Details(
                        List.of(), new GameEvaluationResponse.Details.BoundaryToneFeedback("weak"),
                        new GameEvaluationResponse.Details.DynamicStressFeedback("monotone")
                ),
                new GameEvaluationResponse.Scores(65.0, 80.0, 35.0, 65.0, 50.0, 60.0, 80.0, 95.0),
                1, 0, true, result
        );
        given(gameService.evaluate(eq(USER_ID), eq(1), eq(1), any())).willReturn(response);

        MockMultipartFile audio = new MockMultipartFile("file", "test.wav", "audio/wav", "bytes".getBytes());

        mockMvc.perform(multipart("/game/levels/1/rounds/1/evaluate")
                        .file(audio)
                        .with(authentication(AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameOver").value(true))
                .andExpect(jsonPath("$.data.hearts").value(0))
                .andExpect(jsonPath("$.data.result.hearts").value(0))
                .andExpect(jsonPath("$.data.result.finalScore").value(65.0));
    }

    @Test
    @DisplayName("POST evaluate: 이전 라운드 미완료면 400을 반환한다")
    void evaluate_이전라운드없음_400() throws Exception {
        given(gameService.evaluate(eq(USER_ID), eq(1), eq(2), any()))
                .willThrow(new CustomException(ErrorCode.GAME_ROUND_INVALID));

        MockMultipartFile audio = new MockMultipartFile("file", "test.wav", "audio/wav", "bytes".getBytes());

        mockMvc.perform(multipart("/game/levels/1/rounds/2/evaluate")
                        .file(audio)
                        .with(authentication(AUTH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.GAME_ROUND_INVALID.getCode()));
    }

    @Test
    @DisplayName("POST evaluate: 음성 인식 실패면 400을 반환한다")
    void evaluate_음성인식실패_400() throws Exception {
        given(gameService.evaluate(eq(USER_ID), eq(1), eq(1), any()))
                .willThrow(new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED));

        MockMultipartFile audio = new MockMultipartFile("file", "test.wav", "audio/wav", "bytes".getBytes());

        mockMvc.perform(multipart("/game/levels/1/rounds/1/evaluate")
                        .file(audio)
                        .with(authentication(AUTH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VOICE_RECOGNITION_FAILED.getCode()));
    }

    // ── GET /game/leaderboard ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /game/leaderboard: 정상 조회 시 200과 리더보드 정보를 반환한다")
    void getLeaderboard_200() throws Exception {
        GameLeaderboardResponse response = new GameLeaderboardResponse(
                "GOLD", 2,
                new GameLeaderboardResponse.TimeUntilReset(3, 12, 30),
                List.of(
                        new GameLeaderboardResponse.RankerInfo(1, "first", 1000.0, false),
                        new GameLeaderboardResponse.RankerInfo(2, "second", 800.0, false),
                        new GameLeaderboardResponse.RankerInfo(3, "me", 700.0, true)
                ),
                List.of(
                        new GameLeaderboardResponse.RankerInfo(2, "second", 800.0, false),
                        new GameLeaderboardResponse.RankerInfo(3, "me", 700.0, true),
                        new GameLeaderboardResponse.RankerInfo(4, "below", 500.0, false)
                )
        );
        given(gameService.getLeaderboard(USER_ID)).willReturn(response);

        mockMvc.perform(get("/game/leaderboard")
                        .with(authentication(AUTH)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("GOLD"))
                .andExpect(jsonPath("$.data.promotionCount").value(2))
                .andExpect(jsonPath("$.data.timeUntilReset.days").value(3))
                .andExpect(jsonPath("$.data.timeUntilReset.hours").value(12))
                .andExpect(jsonPath("$.data.topRankers[0].nickname").value("first"))
                .andExpect(jsonPath("$.data.topRankers[2].isMe").value(true))
                .andExpect(jsonPath("$.data.nearbyRankers[1].isMe").value(true));
    }

    @Test
    @DisplayName("GET /game/leaderboard: freeze 상태이면 403을 반환한다")
    void getLeaderboard_freeze_403() throws Exception {
        given(gameService.getLeaderboard(USER_ID))
                .willThrow(new CustomException(ErrorCode.USER_IS_FROZEN));

        mockMvc.perform(get("/game/leaderboard")
                        .with(authentication(AUTH)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_IS_FROZEN.getCode()));
    }

    // ── GET /game/profile ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /game/profile: 티어, 주간 점수, freeze 여부를 200으로 반환한다")
    void getProfile_200() throws Exception {
        given(gameService.getProfile(USER_ID))
                .willReturn(new GameProfileResponse("PLATINUM", 350.5, false));

        mockMvc.perform(get("/game/profile")
                        .with(authentication(AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PLATINUM"))
                .andExpect(jsonPath("$.data.weeklyScore").value(350.5))
                .andExpect(jsonPath("$.data.frozen").value(false));
    }

    @Test
    @DisplayName("GET /game/profile: 프로필이 없으면 404를 반환한다")
    void getProfile_없음_404() throws Exception {
        given(gameService.getProfile(USER_ID))
                .willThrow(new CustomException(ErrorCode.GAME_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/game/profile")
                        .with(authentication(AUTH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.GAME_PROFILE_NOT_FOUND.getCode()));
    }
}

package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.dto.res.GameTodayResponse;
import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.util.WordMasker;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameService.getToday() - 오늘의 게임 현황 조회")
class GameServiceGetTodayTest {

    @InjectMocks private GameService gameService;

    @Mock private UserGameProfileRepository  userGameProfileRepository;
    @Mock private DailyGameSentenceRepository dailyGameSentenceRepository;
    @Mock private DailyBestRecordRepository  dailyBestRecordRepository;
    @Mock private PythonApiClient            pythonApiClient;
    @Mock private S3Uploader                 s3Uploader;
    @Mock private GameSessionStore           gameSessionStore;
    @Mock private GameWriter                 gameWriter;
    @Spy  private ObjectMapper               objectMapper;

    private static final Long USER_ID = 1L;

    private DailyBestRecord buildBestRecord(int level, int hearts, double finalScore) {
        User user = User.builder().email("u@e.com").nickname("nick").provider("KAKAO").providerId("p1").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        GameSentence gs = GameSentence.builder().level(level).content("Test sentence.")
                .featuresUrl(null).wordTimestamps("[]").build();
        GameRecord record = GameRecord.builder()
                .user(user).level(level).playedDate(LocalDate.now())
                .hearts(hearts)
                .avgTotalScore(BigDecimal.valueOf(80.0))
                .avgWordRhythmScore(BigDecimal.valueOf(80.0))
                .avgDynamicStressScore(BigDecimal.valueOf(70.0))
                .avgWordAccuracy(BigDecimal.valueOf(75.0))
                .finalScore(BigDecimal.valueOf(finalScore))
                .build();
        ReflectionTestUtils.setField(record, "id", (long) (level * 10));

        return DailyBestRecord.builder()
                .userId(USER_ID).level(level).recordDate(LocalDate.now())
                .gameRecord(record).bestFinalScore(BigDecimal.valueOf(finalScore))
                .build();
    }

    @Test
    @DisplayName("레벨 1은 항상 해금 상태이다")
    void getToday_레벨1_항상해금() {
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(eq(USER_ID), anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        assertThat(response.levels().get(0).level()).isEqualTo(1);
        assertThat(response.levels().get(0).unlocked()).isTrue();
    }

    @Test
    @DisplayName("레벨 2는 레벨 1 최고 기록의 hearts >= 1이면 해금된다")
    void getToday_레벨2_해금() {
        LocalDate today = LocalDate.now();
        DailyBestRecord level1Best = buildBestRecord(1, 2, 88.0); // hearts=2

        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 1, today))
                .willReturn(Optional.of(level1Best));
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 2, today))
                .willReturn(Optional.empty());
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 3, today))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        assertThat(response.levels().get(1).unlocked()).isTrue();
    }

    @Test
    @DisplayName("레벨 2는 레벨 1 최고 기록이 없으면 잠긴다")
    void getToday_레벨2_레벨1기록없음_잠김() {
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(eq(USER_ID), anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        assertThat(response.levels().get(1).unlocked()).isFalse();
    }

    @Test
    @DisplayName("레벨 2는 레벨 1 최고 기록의 hearts == 0이면 잠긴다")
    void getToday_레벨2_hearts0_잠김() {
        LocalDate today = LocalDate.now();
        DailyBestRecord level1Best = buildBestRecord(1, 0, 60.0); // hearts=0

        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 1, today))
                .willReturn(Optional.of(level1Best));
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 2, today))
                .willReturn(Optional.empty());
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 3, today))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        assertThat(response.levels().get(1).unlocked()).isFalse();
    }

    @Test
    @DisplayName("오늘 플레이 기록이 있으면 todayBest가 채워진다")
    void getToday_오늘기록있음_todayBest() {
        LocalDate today = LocalDate.now();
        DailyBestRecord level1Best = buildBestRecord(1, 3, 99.0);

        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 1, today))
                .willReturn(Optional.of(level1Best));
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 2, today))
                .willReturn(Optional.empty());
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(USER_ID, 3, today))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        GameTodayResponse.LevelStatus lv1 = response.levels().get(0);
        assertThat(lv1.todayBest()).isNotNull();
        assertThat(lv1.todayBest().hearts()).isEqualTo(3);
        assertThat(lv1.todayBest().finalScore()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("오늘 플레이 기록이 없으면 todayBest가 null이다")
    void getToday_오늘기록없음_todayBestNull() {
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(eq(USER_ID), anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        assertThat(response.levels()).hasSize(3);
        response.levels().forEach(lv -> assertThat(lv.todayBest()).isNull());
    }

    @Test
    @DisplayName("응답에 레벨 1, 2, 3이 모두 포함된다")
    void getToday_레벨3개반환() {
        given(dailyBestRecordRepository.findByUserIdAndLevelAndRecordDate(eq(USER_ID), anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());

        GameTodayResponse response = gameService.getToday(USER_ID);

        List<Integer> levels = response.levels().stream().map(GameTodayResponse.LevelStatus::level).toList();
        assertThat(levels).containsExactly(1, 2, 3);
    }
}

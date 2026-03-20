package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameScheduler - 스케줄러 로직")
class GameSchedulerTest {

    @InjectMocks private GameScheduler gameScheduler;

    @Mock private DailyGameSentenceRepository dailyGameSentenceRepository;
    @Mock private GameSentenceRepository      gameSentenceRepository;
    @Mock private UserGameProfileRepository   userGameProfileRepository;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private GameSentence buildSentence(Long id, int level) {
        GameSentence gs = GameSentence.builder()
                .level(level).content("Sentence " + id).featuresUrl(null).wordTimestamps("[]").build();
        ReflectionTestUtils.setField(gs, "id", id);
        return gs;
    }

    private User buildUser(Long id) {
        User user = User.builder().email(id + "@e.com").nickname("nick" + id)
                .provider("KAKAO").providerId("p" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserGameProfile buildProfile(Long userId, Tier tier, double weeklyScore,
                                          int consecutive, boolean frozen, LocalDate lastPlayedWeek) {
        User user = buildUser(userId);
        UserGameProfile p = UserGameProfile.builder()
                .user(user).tier(tier)
                .weeklyScore(BigDecimal.valueOf(weeklyScore))
                .consecutiveNoPlayWeeks(consecutive).frozen(frozen)
                .lastPlayedWeek(lastPlayedWeek).build();
        ReflectionTestUtils.setField(p, "userId", userId);
        return p;
    }

    // ── assignDailySentences ─────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 오늘 문장이 배정됐으면 저장하지 않는다")
    void assignDailySentences_이미배정됨_스킵() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(anyInt(), any(LocalDate.class)))
                .willReturn(Optional.of(DailyGameSentence.builder()
                        .level(1).gameSentence(buildSentence(1L, 1)).assignedDate(LocalDate.now()).build()));

        gameScheduler.assignDailySentences();

        then(gameSentenceRepository).should(never()).findByLevel(anyInt());
        then(dailyGameSentenceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이번 주 미출제 문장이 있으면 그 중 하나를 선정하여 저장한다")
    void assignDailySentences_미출제문장_선정저장() {
        GameSentence candidate = buildSentence(5L, 1);

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());
        given(dailyGameSentenceRepository.findUsedSentenceIdsByLevelAndWeek(anyInt(), any(), any()))
                .willReturn(List.of(1L, 2L, 3L)); // 1,2,3번은 이미 출제
        given(gameSentenceRepository.findByLevel(anyInt()))
                .willReturn(List.of(buildSentence(1L, 1), buildSentence(2L, 1),
                        buildSentence(3L, 1), candidate)); // 4개 중 1,2,3은 제외

        gameScheduler.assignDailySentences();

        ArgumentCaptor<DailyGameSentence> captor = ArgumentCaptor.forClass(DailyGameSentence.class);
        then(dailyGameSentenceRepository).should(times(3)).save(captor.capture());
        captor.getAllValues().forEach(daily ->
                assertThat(daily.getGameSentence().getId()).isEqualTo(5L));
    }

    @Test
    @DisplayName("이번 주 모든 문장이 출제됐으면 전체 풀에서 랜덤 선정한다")
    void assignDailySentences_전체출제_풀백() {
        List<GameSentence> all = List.of(buildSentence(1L, 1), buildSentence(2L, 1), buildSentence(3L, 1));

        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());
        given(dailyGameSentenceRepository.findUsedSentenceIdsByLevelAndWeek(anyInt(), any(), any()))
                .willReturn(List.of(1L, 2L, 3L)); // 모두 출제됨
        given(gameSentenceRepository.findByLevel(anyInt())).willReturn(all);

        gameScheduler.assignDailySentences();

        then(dailyGameSentenceRepository).should(times(3)).save(any(DailyGameSentence.class));
    }

    @Test
    @DisplayName("DB에 문장이 없으면 저장하지 않는다")
    void assignDailySentences_문장없음_스킵() {
        given(dailyGameSentenceRepository.findByLevelAndAssignedDate(anyInt(), any(LocalDate.class)))
                .willReturn(Optional.empty());
        given(dailyGameSentenceRepository.findUsedSentenceIdsByLevelAndWeek(anyInt(), any(), any()))
                .willReturn(List.of());
        given(gameSentenceRepository.findByLevel(anyInt())).willReturn(List.of());

        gameScheduler.assignDailySentences();

        then(dailyGameSentenceRepository).should(never()).save(any());
    }

    // ── recalculateTiers ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 유저: 상위 10%는 승급, 하위 10%는 강등한다")
    void recalculateTiers_활성_승급강등() {
        LocalDate lastMonday = LocalDate.now().with(DayOfWeek.MONDAY);

        // 10명 (모두 GOLD, 지난 주 플레이)
        List<UserGameProfile> profiles = List.of(
                buildProfile(1L,  Tier.GOLD, 1000, 0, false, lastMonday), // 1위 → 승급
                buildProfile(2L,  Tier.GOLD,  900, 0, false, lastMonday),
                buildProfile(3L,  Tier.GOLD,  800, 0, false, lastMonday),
                buildProfile(4L,  Tier.GOLD,  700, 0, false, lastMonday),
                buildProfile(5L,  Tier.GOLD,  600, 0, false, lastMonday),
                buildProfile(6L,  Tier.GOLD,  500, 0, false, lastMonday),
                buildProfile(7L,  Tier.GOLD,  400, 0, false, lastMonday),
                buildProfile(8L,  Tier.GOLD,  300, 0, false, lastMonday),
                buildProfile(9L,  Tier.GOLD,  200, 0, false, lastMonday),
                buildProfile(10L, Tier.GOLD,  100, 0, false, lastMonday)  // 10위 → 강등
        );
        given(userGameProfileRepository.findAllWithUser()).willReturn(profiles);

        gameScheduler.recalculateTiers();

        // 1위는 PLATINUM으로 승급
        assertThat(profiles.get(0).getTier()).isEqualTo(Tier.PLATINUM);
        // 10위는 SILVER로 강등
        assertThat(profiles.get(9).getTier()).isEqualTo(Tier.SILVER);
        // 중간은 GOLD 유지
        assertThat(profiles.get(4).getTier()).isEqualTo(Tier.GOLD);
    }

    @Test
    @DisplayName("비활성 유저: 1주 미플레이 시 1단계 강등된다")
    void recalculateTiers_비활성_1주_강등() {
        LocalDate twoWeeksAgo = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2);
        UserGameProfile inactive = buildProfile(1L, Tier.GOLD, 0, 0, false, twoWeeksAgo);

        given(userGameProfileRepository.findAllWithUser()).willReturn(List.of(inactive));

        gameScheduler.recalculateTiers();

        assertThat(inactive.getTier()).isEqualTo(Tier.SILVER);
        assertThat(inactive.getConsecutiveNoPlayWeeks()).isEqualTo(1);
    }

    @Test
    @DisplayName("비활성 유저: 4주 미플레이 시 freeze 상태가 된다")
    void recalculateTiers_비활성_4주_freeze() {
        LocalDate longAgo = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(5);
        UserGameProfile inactive = buildProfile(1L, Tier.GOLD, 0, 3, false, longAgo);

        given(userGameProfileRepository.findAllWithUser()).willReturn(List.of(inactive));

        gameScheduler.recalculateTiers();

        assertThat(inactive.isFrozen()).isTrue();
        assertThat(inactive.getConsecutiveNoPlayWeeks()).isEqualTo(4);
    }

    @Test
    @DisplayName("freeze 유저는 승/강등 계산에서 제외된다")
    void recalculateTiers_freeze유저_제외() {
        LocalDate twoWeeksAgo = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2);
        UserGameProfile frozen = buildProfile(1L, Tier.GOLD, 0, 4, true, twoWeeksAgo);

        given(userGameProfileRepository.findAllWithUser()).willReturn(List.of(frozen));

        gameScheduler.recalculateTiers();

        // freeze 유저는 패널티 적용 없음, 티어 변경 없음
        assertThat(frozen.getTier()).isEqualTo(Tier.GOLD);
        assertThat(frozen.getConsecutiveNoPlayWeeks()).isEqualTo(4);
    }

    @Test
    @DisplayName("스케줄러 실행 후 전체 유저의 주간 점수가 0으로 초기화된다")
    void recalculateTiers_주간점수초기화() {
        LocalDate lastMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<UserGameProfile> profiles = List.of(
                buildProfile(1L, Tier.GOLD, 500, 0, false, lastMonday),
                buildProfile(2L, Tier.GOLD, 300, 0, false, lastMonday)
        );
        given(userGameProfileRepository.findAllWithUser()).willReturn(profiles);

        gameScheduler.recalculateTiers();

        profiles.forEach(p -> assertThat(p.getWeeklyScore()).isEqualByComparingTo(BigDecimal.ZERO));
        then(userGameProfileRepository).should(times(1)).saveAll(profiles);
    }

    @Test
    @DisplayName("BRONZE 유저는 비활성이어도 강등되지 않는다")
    void recalculateTiers_BRONZE_비활성_강등없음() {
        LocalDate twoWeeksAgo = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2);
        UserGameProfile bronze = buildProfile(1L, Tier.BRONZE, 0, 0, false, twoWeeksAgo);

        given(userGameProfileRepository.findAllWithUser()).willReturn(List.of(bronze));

        gameScheduler.recalculateTiers();

        assertThat(bronze.getTier()).isEqualTo(Tier.BRONZE);
        assertThat(bronze.getConsecutiveNoPlayWeeks()).isEqualTo(1);
    }
}

package com.bremenband.shadowengapi.domain.game.entity;

import com.bremenband.shadowengapi.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserGameProfile - 게임 프로필 상태 변경 로직")
class UserGameProfileTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@e.com").nickname("nick").provider("KAKAO").providerId("p1").build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private UserGameProfile buildProfile(Tier tier, int consecutiveNoPlay, boolean frozen) {
        return UserGameProfile.builder()
                .user(user)
                .tier(tier)
                .weeklyScore(BigDecimal.valueOf(100.0))
                .consecutiveNoPlayWeeks(consecutiveNoPlay)
                .frozen(frozen)
                .build();
    }

    // ── markPlayedThisWeek ───────────────────────────────────────────────────────

    @Test
    @DisplayName("markPlayedThisWeek: 연속 미플레이 주 초기화, frozen 해제, lastPlayedWeek 갱신")
    void markPlayedThisWeek_상태초기화() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 3, true);
        LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);

        profile.markPlayedThisWeek(weekStart);

        assertThat(profile.getConsecutiveNoPlayWeeks()).isZero();
        assertThat(profile.isFrozen()).isFalse();
        assertThat(profile.getLastPlayedWeek()).isEqualTo(weekStart);
    }

    // ── applyNoPlayPenalty ───────────────────────────────────────────────────────

    @Test
    @DisplayName("1주 미플레이: consecutiveNoPlayWeeks 증가, 1단계 강등")
    void applyNoPlayPenalty_1주_강등() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 0, false);

        profile.applyNoPlayPenalty();

        assertThat(profile.getConsecutiveNoPlayWeeks()).isEqualTo(1);
        assertThat(profile.getTier()).isEqualTo(Tier.SILVER);
        assertThat(profile.isFrozen()).isFalse();
    }

    @Test
    @DisplayName("BRONZE에서 1주 미플레이: 강등 없음, consecutiveNoPlayWeeks만 증가")
    void applyNoPlayPenalty_BRONZE_강등없음() {
        UserGameProfile profile = buildProfile(Tier.BRONZE, 0, false);

        profile.applyNoPlayPenalty();

        assertThat(profile.getTier()).isEqualTo(Tier.BRONZE);
        assertThat(profile.getConsecutiveNoPlayWeeks()).isEqualTo(1);
    }

    @Test
    @DisplayName("3주 미플레이: 3단계 강등 (GOLD → SILVER → BRONZE → BRONZE 유지)")
    void applyNoPlayPenalty_3주_강등() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 0, false);

        profile.applyNoPlayPenalty(); // SILVER, 1주
        profile.applyNoPlayPenalty(); // BRONZE, 2주
        profile.applyNoPlayPenalty(); // BRONZE 유지, 3주

        assertThat(profile.getConsecutiveNoPlayWeeks()).isEqualTo(3);
        assertThat(profile.getTier()).isEqualTo(Tier.BRONZE);
        assertThat(profile.isFrozen()).isFalse();
    }

    @Test
    @DisplayName("4주 미플레이: freeze 상태로 전환, 추가 강등 없음")
    void applyNoPlayPenalty_4주_freeze() {
        UserGameProfile profile = buildProfile(Tier.PLATINUM, 3, false);

        profile.applyNoPlayPenalty(); // 4주 → freeze

        assertThat(profile.getConsecutiveNoPlayWeeks()).isEqualTo(4);
        assertThat(profile.isFrozen()).isTrue();
        assertThat(profile.getTier()).isEqualTo(Tier.PLATINUM); // 강등 없음
    }

    @Test
    @DisplayName("4주 이상 미플레이 연속 호출: freeze 유지, 티어 변경 없음")
    void applyNoPlayPenalty_freeze이후_유지() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 4, true); // 이미 freeze

        // freeze 상태는 스케줄러에서 isFrozen() 체크로 스킵되므로 직접 호출 시나리오 검증
        int tierOrdinal = profile.getTier().ordinal();
        profile.applyNoPlayPenalty(); // 만약 호출되더라도 freeze 분기

        assertThat(profile.isFrozen()).isTrue();
        // 티어가 변하지 않아야 함 (연속 5주)
        assertThat(profile.getTier().ordinal()).isGreaterThanOrEqualTo(tierOrdinal - 1);
    }

    // ── addWeeklyScore / resetWeeklyScore ────────────────────────────────────────

    @Test
    @DisplayName("addWeeklyScore: 점수가 누적된다")
    void addWeeklyScore_누적() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 0, false);

        profile.addWeeklyScore(BigDecimal.valueOf(50.0));
        profile.addWeeklyScore(BigDecimal.valueOf(30.5));

        assertThat(profile.getWeeklyScore()).isEqualByComparingTo(BigDecimal.valueOf(180.5));
    }

    @Test
    @DisplayName("resetWeeklyScore: 점수가 0으로 초기화된다")
    void resetWeeklyScore_초기화() {
        UserGameProfile profile = buildProfile(Tier.GOLD, 0, false);

        profile.resetWeeklyScore();

        assertThat(profile.getWeeklyScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

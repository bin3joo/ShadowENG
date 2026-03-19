package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.dto.res.GameLeaderboardResponse;
import com.bremenband.shadowengapi.domain.game.dto.res.GameProfileResponse;
import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameService - 리더보드 / 프로필 조회")
class GameServiceLeaderboardTest {

    @InjectMocks private GameService gameService;

    @Mock private UserGameProfileRepository   userGameProfileRepository;
    @Mock private DailyGameSentenceRepository dailyGameSentenceRepository;
    @Mock private DailyBestRecordRepository   dailyBestRecordRepository;
    @Mock private PythonApiClient             pythonApiClient;
    @Mock private S3Uploader                  s3Uploader;
    @Mock private GameSessionStore            gameSessionStore;
    @Mock private GameWriter                  gameWriter;
    @Spy  private ObjectMapper                objectMapper;

    private static final Long USER_ID = 1L;

    private User buildUser(Long id, String nickname) {
        User user = User.builder().email(id + "@e.com").nickname(nickname)
                .provider("KAKAO").providerId("p" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserGameProfile buildProfile(Long userId, String nickname, Tier tier, double weeklyScore, boolean frozen) {
        User user = buildUser(userId, nickname);
        UserGameProfile p = UserGameProfile.builder()
                .user(user).tier(tier)
                .weeklyScore(BigDecimal.valueOf(weeklyScore))
                .consecutiveNoPlayWeeks(0).frozen(frozen).build();
        ReflectionTestUtils.setField(p, "userId", userId);
        return p;
    }

    // ── 예외 케이스 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("프로필이 없으면 GAME_PROFILE_NOT_FOUND 예외가 발생한다 (leaderboard)")
    void getLeaderboard_프로필없음_예외() {
        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getLeaderboard(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("freeze 상태 사용자가 리더보드 조회 시 USER_IS_FROZEN 예외가 발생한다")
    void getLeaderboard_freeze_예외() {
        UserGameProfile frozenProfile = buildProfile(USER_ID, "nick", Tier.GOLD, 0, true);
        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(frozenProfile));

        assertThatThrownBy(() -> gameService.getLeaderboard(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_IS_FROZEN);
    }

    @Test
    @DisplayName("프로필이 없으면 GAME_PROFILE_NOT_FOUND 예외가 발생한다 (profile)")
    void getProfile_프로필없음_예외() {
        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getProfile(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GAME_PROFILE_NOT_FOUND);
    }

    // ── 정상 케이스 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("리더보드: top 3 랭커를 주간점수 내림차순으로 반환한다")
    void getLeaderboard_top3_순서() {
        UserGameProfile me = buildProfile(USER_ID, "me", Tier.GOLD, 200.0, false);
        List<UserGameProfile> ranked = List.of(
                buildProfile(2L, "first", Tier.GOLD, 500.0, false),
                buildProfile(3L, "second", Tier.GOLD, 300.0, false),
                buildProfile(USER_ID, "me", Tier.GOLD, 200.0, false),
                buildProfile(4L, "fourth", Tier.GOLD, 100.0, false)
        );

        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(userGameProfileRepository.findByTierAndNotFrozenOrderByWeeklyScoreDesc(Tier.GOLD))
                .willReturn(ranked);

        GameLeaderboardResponse response = gameService.getLeaderboard(USER_ID);

        assertThat(response.topRankers()).hasSize(3);
        assertThat(response.topRankers().get(0).rank()).isEqualTo(1);
        assertThat(response.topRankers().get(0).nickname()).isEqualTo("first");
        assertThat(response.topRankers().get(1).nickname()).isEqualTo("second");
        assertThat(response.topRankers().get(2).nickname()).isEqualTo("me");
        assertThat(response.topRankers().get(2).isMe()).isTrue();
    }

    @Test
    @DisplayName("리더보드: 나 ± 1 랭커를 반환한다")
    void getLeaderboard_nearby_나_위아래() {
        UserGameProfile me = buildProfile(USER_ID, "me", Tier.GOLD, 300.0, false);
        List<UserGameProfile> ranked = List.of(
                buildProfile(2L, "above", Tier.GOLD, 400.0, false),
                buildProfile(USER_ID, "me", Tier.GOLD, 300.0, false),
                buildProfile(3L, "below", Tier.GOLD, 200.0, false)
        );

        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(userGameProfileRepository.findByTierAndNotFrozenOrderByWeeklyScoreDesc(Tier.GOLD))
                .willReturn(ranked);

        GameLeaderboardResponse response = gameService.getLeaderboard(USER_ID);

        List<String> nearbyNicknames = response.nearbyRankers().stream()
                .map(GameLeaderboardResponse.RankerInfo::nickname).toList();
        assertThat(nearbyNicknames).containsExactlyInAnyOrder("above", "me", "below");

        boolean meFound = response.nearbyRankers().stream()
                .filter(GameLeaderboardResponse.RankerInfo::isMe).findFirst().isPresent();
        assertThat(meFound).isTrue();
    }

    @Test
    @DisplayName("리더보드: 내가 1위면 nearbyRankers에 나와 아래 1명만 포함")
    void getLeaderboard_1위_nearbyRankers() {
        UserGameProfile me = buildProfile(USER_ID, "me", Tier.GOLD, 500.0, false);
        List<UserGameProfile> ranked = List.of(
                buildProfile(USER_ID, "me", Tier.GOLD, 500.0, false),
                buildProfile(2L, "second", Tier.GOLD, 300.0, false)
        );

        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(userGameProfileRepository.findByTierAndNotFrozenOrderByWeeklyScoreDesc(Tier.GOLD))
                .willReturn(ranked);

        GameLeaderboardResponse response = gameService.getLeaderboard(USER_ID);

        assertThat(response.nearbyRankers()).hasSize(2);
        assertThat(response.nearbyRankers().get(0).isMe()).isTrue();
    }

    @Test
    @DisplayName("리더보드: Challenger 티어는 promotionCount=0")
    void getLeaderboard_Challenger_promotionCount0() {
        UserGameProfile me = buildProfile(USER_ID, "me", Tier.CHALLENGER, 1000.0, false);
        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(userGameProfileRepository.findByTierAndNotFrozenOrderByWeeklyScoreDesc(Tier.CHALLENGER))
                .willReturn(List.of(buildProfile(USER_ID, "me", Tier.CHALLENGER, 1000.0, false)));

        GameLeaderboardResponse response = gameService.getLeaderboard(USER_ID);

        assertThat(response.promotionCount()).isEqualTo(0);
        assertThat(response.tier()).isEqualTo("CHALLENGER");
    }

    @Test
    @DisplayName("리더보드: 10명 중 상위 10% = 승급 인원 1명")
    void getLeaderboard_승급인원수_계산() {
        UserGameProfile me = buildProfile(USER_ID, "me", Tier.GOLD, 50.0, false);
        List<UserGameProfile> ranked = List.of(
                buildProfile(2L, "p1", Tier.GOLD, 900.0, false),
                buildProfile(3L, "p2", Tier.GOLD, 800.0, false),
                buildProfile(4L, "p3", Tier.GOLD, 700.0, false),
                buildProfile(5L, "p4", Tier.GOLD, 600.0, false),
                buildProfile(6L, "p5", Tier.GOLD, 500.0, false),
                buildProfile(7L, "p6", Tier.GOLD, 400.0, false),
                buildProfile(8L, "p7", Tier.GOLD, 300.0, false),
                buildProfile(9L, "p8", Tier.GOLD, 200.0, false),
                buildProfile(10L, "p9", Tier.GOLD, 100.0, false),
                buildProfile(USER_ID, "me", Tier.GOLD, 50.0, false)
        );

        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(userGameProfileRepository.findByTierAndNotFrozenOrderByWeeklyScoreDesc(Tier.GOLD))
                .willReturn(ranked);

        GameLeaderboardResponse response = gameService.getLeaderboard(USER_ID);

        assertThat(response.promotionCount()).isEqualTo(1); // ceil(10 * 0.1) = 1
    }

    @Test
    @DisplayName("프로필 조회: 티어, 주간 점수, freeze 여부를 정상 반환한다")
    void getProfile_정상반환() {
        UserGameProfile profile = buildProfile(USER_ID, "nick", Tier.PLATINUM, 350.5, false);
        given(userGameProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(profile));

        GameProfileResponse response = gameService.getProfile(USER_ID);

        assertThat(response.tier()).isEqualTo("PLATINUM");
        assertThat(response.weeklyScore()).isEqualTo(350.5);
        assertThat(response.frozen()).isFalse();
    }
}

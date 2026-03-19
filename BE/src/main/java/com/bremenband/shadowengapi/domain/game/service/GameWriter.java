package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 게임 결과 DB 저장 담당 (Self-invocation 트랜잭션 문제 회피용 분리 컴포넌트).
 */
@Component
@RequiredArgsConstructor
public class GameWriter {

    private final UserRepository userRepository;
    private final GameRecordRepository gameRecordRepository;
    private final DailyBestRecordRepository dailyBestRecordRepository;
    private final UserGameProfileRepository userGameProfileRepository;

    /**
     * 게임 종료 시 GameRecord, DailyBestRecord, UserGameProfile 를 한 트랜잭션에서 저장.
     */
    @Transactional
    public GameRecord saveGameResult(Long userId, int level, LocalDate today,
                                     int hearts, double avgTotal, double avgSpeed,
                                     double avgDynamic, double avgBoundary,
                                     double finalScore, double cumulative) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. GameRecord 저장
        GameRecord record = gameRecordRepository.save(GameRecord.builder()
                .user(user)
                .level(level)
                .playedDate(today)
                .hearts(hearts)
                .avgTotalScore(bd(avgTotal))
                .avgSpeedSimilarity(bd(avgSpeed))
                .avgDynamicStressScore(bd(avgDynamic))
                .avgBoundaryToneScore(bd(avgBoundary))
                .finalScore(bd(finalScore))
                .cumulativeScore(bd(cumulative))
                .build());

        // 2. DailyBestRecord 갱신 (최고 finalScore만 저장)
        DailyBestRecord best = dailyBestRecordRepository
                .findByUserIdAndLevelAndRecordDate(userId, level, today)
                .orElse(null);

        if (best == null) {
            dailyBestRecordRepository.save(DailyBestRecord.builder()
                    .userId(userId)
                    .level(level)
                    .recordDate(today)
                    .gameRecord(record)
                    .bestFinalScore(bd(finalScore))
                    .build());
        } else if (finalScore > best.getBestFinalScore().doubleValue()) {
            best.update(record, bd(finalScore));
        }

        // 3. UserGameProfile 주간 점수 누적 + 플레이 주 갱신
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        UserGameProfile profile = userGameProfileRepository.findByUser_Id(userId)
                .orElseGet(() -> UserGameProfile.builder()
                        .user(user)
                        .tier(Tier.GOLD)
                        .weeklyScore(BigDecimal.ZERO)
                        .consecutiveNoPlayWeeks(0)
                        .frozen(false)
                        .build());

        profile.addWeeklyScore(bd(finalScore));
        profile.markPlayedThisWeek(weekStart);
        userGameProfileRepository.save(profile);

        return record;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}

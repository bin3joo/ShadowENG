package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameRecord saveGameResult(Long userId, int level, LocalDate today,
                                     int hearts, double avgTotal, double avgWordRhythm,
                                     double avgDynamic, double avgWordAccuracy,
                                     double finalScore) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. GameRecord 저장
        GameRecord record = gameRecordRepository.save(GameRecord.builder()
                .user(user)
                .level(level)
                .playedDate(today)
                .hearts(hearts)
                .avgTotalScore(bd(avgTotal))
                .avgWordRhythmScore(bd(avgWordRhythm))
                .avgDynamicStressScore(bd(avgDynamic))
                .avgWordAccuracy(bd(avgWordAccuracy))
                .finalScore(bd(finalScore))
                .build());

        // 2. DailyBestRecord 갱신 (최고 finalScore만 저장)
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        // 이번 주 해당 레벨의 기존 최고 점수 (오늘 기록 갱신 전에 조회)
        BigDecimal weeklyBest = dailyBestRecordRepository
                .findWeeklyBestScore(userId, level, weekStart, today)
                .orElse(BigDecimal.ZERO);

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
        // 이번 주 해당 레벨의 최고 점수 대비 향상분만 weeklyScore에 반영
        UserGameProfile profile = userGameProfileRepository.findByUser_Id(userId)
                .orElseGet(() -> UserGameProfile.builder()
                        .user(user)
                        .tier(Tier.GOLD)
                        .weeklyScore(BigDecimal.ZERO)
                        .consecutiveNoPlayWeeks(0)
                        .frozen(false)
                        .build());

        BigDecimal delta = bd(finalScore).subtract(weeklyBest).max(BigDecimal.ZERO);
        profile.addWeeklyScore(delta);
        profile.markPlayedThisWeek(weekStart);
        userGameProfileRepository.save(profile);

        return record;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}

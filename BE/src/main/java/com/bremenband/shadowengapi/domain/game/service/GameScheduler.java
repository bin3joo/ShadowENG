package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameScheduler {

    private static final int MAX_LEVEL = 3;

    private final DailyGameSentenceRepository dailyGameSentenceRepository;
    private final GameSentenceRepository gameSentenceRepository;
    private final UserGameProfileRepository userGameProfileRepository;

    /**
     * 매일 00:01 에 레벨별 오늘의 문장을 선정.
     * 당주(월~일) 출제된 문장은 제외 후 랜덤 선택.
     * 모든 문장이 이미 출제됐으면 전체 풀에서 다시 선택.
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void assignDailySentences() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
        log.info("[GameScheduler] assignDailySentences: date={}", today);

        for (int level = 1; level <= MAX_LEVEL; level++) {
            if (dailyGameSentenceRepository.findByLevelAndAssignedDate(level, today).isPresent()) {
                log.info("[GameScheduler] already assigned: level={}", level);
                continue;
            }

            List<Long> usedIds = dailyGameSentenceRepository
                    .findUsedSentenceIdsByLevelAndWeek(level, weekStart, weekEnd);

            List<GameSentence> candidates = gameSentenceRepository.findByLevel(level)
                    .stream()
                    .filter(s -> !usedIds.contains(s.getId()))
                    .collect(Collectors.toList());

            // 모든 문장이 이번 주에 출제됐으면 전체 풀 사용
            if (candidates.isEmpty()) {
                candidates = gameSentenceRepository.findByLevel(level);
            }

            if (candidates.isEmpty()) {
                log.warn("[GameScheduler] no sentences for level={}", level);
                continue;
            }

            GameSentence selected = candidates.get(new Random().nextInt(candidates.size()));
            dailyGameSentenceRepository.save(DailyGameSentence.builder()
                    .level(level)
                    .gameSentence(selected)
                    .assignedDate(today)
                    .build());

            log.info("[GameScheduler] assigned: level={}, sentenceId={}", level, selected.getId());
        }
    }

    /**
     * 매주 월요일 00:00 에 티어 재계산.
     * 1) 지난 주 활성 유저: 티어 내 상위 10% 승급, 하위 10% 강등
     * 2) 지난 주 비활성 유저: 1단계 강등 패널티 (4주 이상 → freeze)
     * 3) 전체 주간 점수 초기화
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void recalculateTiers() {
        log.info("[GameScheduler] recalculateTiers start");
        LocalDate today = LocalDate.now();
        LocalDate lastWeekStart = today.minusWeeks(1);

        List<UserGameProfile> all = userGameProfileRepository.findAllWithUser();

        List<UserGameProfile> active   = new ArrayList<>();
        List<UserGameProfile> inactive = new ArrayList<>();

        for (UserGameProfile p : all) {
            if (p.isFrozen()) continue; // freeze 유저는 양쪽 제외
            boolean playedLastWeek = p.getLastPlayedWeek() != null
                    && !p.getLastPlayedWeek().isBefore(lastWeekStart);
            (playedLastWeek ? active : inactive).add(p);
        }

        // 활성 유저 — 티어별 승/강등
        Map<Tier, List<UserGameProfile>> byTier = active.stream()
                .collect(Collectors.groupingBy(UserGameProfile::getTier));

        byTier.forEach((tier, users) -> {
            users.sort(Comparator.comparing(UserGameProfile::getWeeklyScore).reversed());
            int total = users.size();
            int upCount   = tier.isHighest() ? 0 : Math.max(1, (int) Math.ceil(total * 0.1));
            int downCount = tier.isLowest()  ? 0 : Math.max(1, (int) Math.ceil(total * 0.1));
            for (int i = 0; i < total; i++) {
                if (!tier.isHighest() && i < upCount) {
                    users.get(i).promote();
                } else if (!tier.isLowest() && i >= total - downCount) {
                    users.get(i).demote();
                }
            }
        });

        // 비활성 유저 — 패널티
        inactive.forEach(UserGameProfile::applyNoPlayPenalty);

        // 전체 주간 점수 초기화 (freeze 포함)
        all.forEach(UserGameProfile::resetWeeklyScore);

        userGameProfileRepository.saveAll(all);
        log.info("[GameScheduler] recalculateTiers done: active={}, inactive={}", active.size(), inactive.size());
    }
}

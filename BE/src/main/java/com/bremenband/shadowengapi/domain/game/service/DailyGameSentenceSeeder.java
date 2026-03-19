package com.bremenband.shadowengapi.domain.game.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 시 오늘의 게임 문장이 배정되지 않았으면 즉시 배정한다.
 * GameSentenceSeeder(Order=1) 이후 실행되어 game_sentences 데이터가 존재함을 보장받는다.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class DailyGameSentenceSeeder implements ApplicationRunner {

    private final GameScheduler gameScheduler;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DailyGameSentenceSeeder] assigning today's sentences if not already assigned");
        gameScheduler.assignDailySentences();
    }
}

package com.bremenband.shadowengapi.domain.game.dto.res;

import java.util.List;

public record GameTodayResponse(List<LevelStatus> levels) {

    public record LevelStatus(
            int level,
            boolean unlocked,
            DailyBest todayBest // null if no record today
    ) {}

    public record DailyBest(
            int hearts,
            double finalScore
    ) {}
}

package com.bremenband.shadowengapi.domain.game.dto.res;

public record GameProfileResponse(
        String tier,
        double weeklyScore,
        boolean frozen
) {}

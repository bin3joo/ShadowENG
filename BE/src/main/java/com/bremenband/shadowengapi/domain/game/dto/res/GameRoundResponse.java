package com.bremenband.shadowengapi.domain.game.dto.res;

public record GameRoundResponse(
        int level,
        int round,
        String sentence,          // round 1: 전체 문장, round 3: null
        String maskedSentence,    // round 2용 사전 마스킹 문장 (모든 round에서 함께 반환)
        String referenceAudioUrl
) {}

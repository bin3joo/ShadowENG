package com.bremenband.shadowengapi.domain.game.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GameLeaderboardResponse(
        String tier,
        int totalMembers,            // 티어 전체 인원 수
        int promotionCount,          // 이번 주 승급 예정 인원 수
        TimeUntilReset timeUntilReset,
        List<RankerInfo> topRankers,    // 티어 내 1~3위
        List<RankerInfo> nearbyRankers  // 나 ± 1위
) {
    public record TimeUntilReset(int days, int hours, int minutes) {}

    public record RankerInfo(
            int rank,
            String nickname,
            double weeklyScore,
            @JsonProperty("isMe")
            boolean isMe
    ) {}
}

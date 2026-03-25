package com.bremenband.shadowengapi.domain.user.dto.res;

import com.bremenband.shadowengapi.domain.study.dto.res.LatestActiveSessionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.util.List;

@Schema(description = "메인 화면 응답 DTO")
public record MainResponse(

        @Schema(description = "이번주(일~토) 중 문장 학습을 1회 이상 완료한 요일 목록")
        List<DayOfWeek> weeklyStudyDays,

        @Schema(description = "가장 최근 학습 세션 정보 (없으면 null)")
        LatestActiveSessionResponse recentSession,

        @Schema(description = "사용자 게임 정보 (게임 미플레이 시 null)")
        GameSummary gameSummary

) {

    @Schema(description = "게임 요약 정보")
    public record GameSummary(

            @Schema(description = "소속 리그 (티어)", example = "GOLD")
            String tier,

            @Schema(description = "닉네임", example = "브레맨")
            String nickname,

            @Schema(description = "현재 순위 (동결 상태이면 0)", example = "3")
            int rank,

            @Schema(description = "이번주 점수", example = "142.5")
            double weeklyScore

    ) {}
}

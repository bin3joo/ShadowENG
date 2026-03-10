package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 학습 세션 조회 응답 DTO")
public record RecentStudySessionResponse(

        @Schema(description = "가장 최근 학습 중인 세션 (없으면 null)")
        LatestActiveSessionResponse latestActiveSession

) {
}

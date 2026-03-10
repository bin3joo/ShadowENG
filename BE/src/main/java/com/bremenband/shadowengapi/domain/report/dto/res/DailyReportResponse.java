package com.bremenband.shadowengapi.domain.report.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "데일리 학습 리포트 응답 DTO")
public record DailyReportResponse(

        @Schema(description = "일자별 학습 데이터")
        List<StudyDayData> studyData

) {

    @Schema(description = "특정 날짜의 학습 데이터")
    public record StudyDayData(

            @Schema(description = "학습 일자", example = "2026-03-02")
            String date,

            @Schema(description = "해당 날짜에 학습한 문장 수", example = "15")
            int studiedSentencesCount

    ) {
    }
}

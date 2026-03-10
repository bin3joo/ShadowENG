package com.bremenband.shadowengapi.domain.study.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학습 세션 생성 응답 DTO")
public record StudySessionCreateResponse(

        @Schema(description = "생성된 세션 ID", example = "12345")
        Long sessionId,

        @Schema(description = "학습 영상 정보")
        VideoData videoData,

        @Schema(description = "전사된 문장 목록")
        List<SentenceData> sentencesData

) {

    @Schema(description = "영상 정보")
    public record VideoData(

            @Schema(description = "유튜브 영상 고유 ID", example = "dQw4w9WgXcQ")
            String videoId,

            @Schema(description = "임베드 URL", example = "https://www.youtube.com/embed/dQw4w9WgXcQ")
            String embedUrl,

            @Schema(description = "영상 제목")
            String title,

            @Schema(description = "썸네일 URL")
            String thumbnailUrl,

            @Schema(description = "영상 총 길이 (초)", example = "212")
            long duration,

            @Schema(description = "채널명", example = "Rick Astley")
            String channelTitle

    ) {
    }

    @Schema(description = "문장 정보")
    public record SentenceData(

            @Schema(description = "문장 고유 ID", example = "1234")
            Long sentenceId,

            @Schema(description = "문장 내용")
            String sentence,

            @Schema(description = "문장 시작 시간 (초)", example = "5.61")
            double startSec,

            @Schema(description = "문장 종료 시간 (초)", example = "10.78")
            double endSec,

            @Schema(description = "문장 지속 시간 (초)", example = "5.17")
            double durationSec,

            @Schema(description = "문장 학습 횟수", example = "0")
            int studyCount

    ) {
    }
}

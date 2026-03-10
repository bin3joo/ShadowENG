package com.bremenband.shadowengapi.domain.study.dto.transcription;

public record TranscribedSentence(
        String content,
        double startSec,
        double endSec,
        double durationSec,
        String wordTimestamps,  // JSON string — evaluate-audio 호출 시 사용
        String features         // JSON string — evaluate-audio 호출 시 사용
) {
}

package com.bremenband.shadowengapi.domain.study.dto.transcription;

public record TranscribedSentence(
        String content,
        double startSec,
        double endSec,
        double durationSec,
        String wordTimestamps,  // JSON string — evaluate-audio 호출 시 사용
        String featuresUrl,     // S3 key — evaluate-audio 호출 시 fetch
        String metadataUrl      // S3 key — 향후 사용 예정 (vocabulary, sentenceKo 등)
) {
}

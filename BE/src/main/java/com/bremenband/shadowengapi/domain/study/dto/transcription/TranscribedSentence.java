package com.bremenband.shadowengapi.domain.study.dto.transcription;

import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;

import java.util.List;

public record TranscribedSentence(
        String content,
        double startSec,
        double endSec,
        double durationSec,
        String wordTimestamps,  // JSON string — evaluate-audio 호출 시 사용
        String featuresUrl,     // S3 key — evaluate-audio 호출 시 fetch
        // Python 원본 데이터 — RDB 저장 및 세션 생성 response에 포함
        String sentenceKo,
        List<String> keyExpressions,
        String difficulty,
        Double difficultyScore,
        List<PythonGenerateReferenceResponse.Vocabulary> vocabulary,
        List<PythonGenerateReferenceResponse.WordTimestamp> wordTimestampList
) {
}

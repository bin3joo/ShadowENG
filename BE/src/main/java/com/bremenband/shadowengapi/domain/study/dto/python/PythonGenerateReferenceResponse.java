package com.bremenband.shadowengapi.domain.study.dto.python;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonGenerateReferenceResponse(

        String status,

        @JsonProperty("translation_success")
        boolean translationSuccess,

        List<Part> parts

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(

            String sentence,

            @JsonProperty("start_sec")
            double startSec,

            @JsonProperty("end_sec")
            double endSec,

            @JsonProperty("duration_sec")
            double durationSec,

            @JsonProperty("word_timestamps")
            List<WordTimestamp> wordTimestamps,

            PythonFeatures features,

            @JsonProperty("sentence_ko")
            String sentenceKo,

            @JsonProperty("key_expressions")
            List<String> keyExpressions,

            String difficulty,

            @JsonProperty("difficulty_score")
            Double difficultyScore,

            List<Vocabulary> vocabulary

    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WordTimestamp(

            String word,
            double start,
            double end,
            double score

    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PythonFeatures(

            @JsonProperty("f0_array")
            List<Double> f0Array,

            @JsonProperty("rms_array")
            List<Double> rmsArray

    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vocabulary(

            String word,

            @JsonProperty("meaning_ko")
            String meaningKo,

            @JsonProperty("phonetic_en")
            String phoneticEn,

            @JsonProperty("phonetic_ko")
            String phoneticKo,

            @JsonProperty("example_en")
            String exampleEn,

            @JsonProperty("example_ko")
            String exampleKo

    ) {
    }
}

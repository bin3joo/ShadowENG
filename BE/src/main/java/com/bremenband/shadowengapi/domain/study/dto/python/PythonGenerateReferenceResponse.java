package com.bremenband.shadowengapi.domain.study.dto.python;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonGenerateReferenceResponse(

        String status,

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
            String sentenceKo

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
}

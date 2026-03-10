package com.bremenband.shadowengapi.domain.study.dto.python;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record PythonEvaluateAudioRequest(

        @JsonProperty("user_audio")
        String userAudio,

        @JsonProperty("user_audio_format")
        String userAudioFormat,

        @JsonProperty("final_script")
        String finalScript,

        @JsonProperty("features")
        JsonNode features,

        @JsonProperty("word_timestamps")
        JsonNode wordTimestamps

) {
}

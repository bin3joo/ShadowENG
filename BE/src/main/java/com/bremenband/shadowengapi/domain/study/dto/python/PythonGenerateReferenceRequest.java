package com.bremenband.shadowengapi.domain.study.dto.python;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonGenerateReferenceRequest(

        @JsonProperty("video_id")
        String youtubeId,

        @JsonProperty("start_sec")
        double startSec,

        @JsonProperty("end_sec")
        double endSec

) {
}

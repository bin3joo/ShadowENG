package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final PythonApiClient pythonApiClient;
    private final S3Uploader s3Uploader;
    private final ObjectMapper objectMapper;

    public List<TranscribedSentence> transcribe(String videoId, double startSec, double endSec) {

        PythonGenerateReferenceResponse response =
                pythonApiClient.generateReference(videoId, startSec, endSec);

        if (!"SUCCESS".equals(response.status())) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return response.parts().stream()
                .map(part -> {
                    String featuresUrl  = s3Uploader.uploadJson(toJson(part.features()),  "features/");
                    String metadataUrl  = s3Uploader.uploadJson(toJson(buildMetadata(part)), "metadata/");
                    return new TranscribedSentence(
                            part.sentence(),
                            part.startSec(),
                            part.endSec(),
                            part.durationSec(),
                            toJson(part.wordTimestamps()),
                            featuresUrl,
                            metadataUrl
                    );
                })
                .toList();
    }

    private Map<String, Object> buildMetadata(PythonGenerateReferenceResponse.Part part) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sentenceKo",      part.sentenceKo());
        map.put("keyExpressions",  part.keyExpressions());
        map.put("difficulty",      part.difficulty());
        map.put("difficultyScore", part.difficultyScore());
        map.put("vocabulary",      part.vocabulary());
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }
}

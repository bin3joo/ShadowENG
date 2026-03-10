package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final PythonApiClient pythonApiClient;
    private final ObjectMapper objectMapper;

    public List<TranscribedSentence> transcribe(String videoId, double startSec, double endSec) {
        PythonGenerateReferenceResponse response =
                pythonApiClient.generateReference(videoId, startSec, endSec);

        if (!"SUCCESS".equals(response.status())) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return response.parts().stream()
                .map(part -> new TranscribedSentence(
                        part.sentence(),
                        part.startSec(),
                        part.endSec(),
                        part.durationSec(),
                        toJson(part.wordTimestamps()),
                        toJson(part.features())
                ))
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }
}

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

import java.util.List;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private static final int MAX_RETRIES = 2;

    private final PythonApiClient pythonApiClient;
    private final S3Uploader s3Uploader;
    private final ObjectMapper objectMapper;

    public List<TranscribedSentence> transcribe(String videoId, double startSec, double endSec) {

        PythonGenerateReferenceResponse response = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            response = pythonApiClient.generateReference(videoId, startSec, endSec);

            if (!"SUCCESS".equals(response.status())) {
                throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
            }

            if (response.translationSuccess()) {
                break;
            }
        }

        if (!response.translationSuccess()) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return response.parts().stream()
                .map(part -> {
                    String featuresUrl = s3Uploader.uploadJson(toJson(part.features()), "features/");
                    List<PythonGenerateReferenceResponse.WordTimestamp> offsetTimestamps = part.wordTimestamps()
                            .stream()
                            .map(wt -> new PythonGenerateReferenceResponse.WordTimestamp(
                                    wt.word(),
                                    wt.start() + startSec,
                                    wt.end() + startSec,
                                    wt.score()
                            ))
                            .toList();
                    return new TranscribedSentence(
                            part.sentence(),
                            part.startSec() + startSec,
                            part.endSec() + startSec,
                            part.durationSec(),
                            toJson(offsetTimestamps),
                            featuresUrl,
                            part.sentenceKo(),
                            part.keyExpressions(),
                            part.difficulty(),
                            part.difficultyScore(),
                            part.vocabulary(),
                            offsetTimestamps
                    );
                })
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

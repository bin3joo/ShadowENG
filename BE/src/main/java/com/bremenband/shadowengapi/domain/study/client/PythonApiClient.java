package com.bremenband.shadowengapi.domain.study.client;

import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonApiClient {

    private final RestClient restClient;
    private final RestClient transcriptionRestClient;

    @Value("${python.api.base-url}")
    private String baseUrl;

    public PythonApiClient(
            @Qualifier("pythonRestClient") RestClient restClient,
            @Qualifier("pythonTranscriptionRestClient") RestClient transcriptionRestClient) {
        this.restClient = restClient;
        this.transcriptionRestClient = transcriptionRestClient;
    }

    public PythonGenerateReferenceResponse generateReference(String videoId, double startSec, double endSec) {
        PythonGenerateReferenceRequest request =
                new PythonGenerateReferenceRequest(videoId, startSec, endSec);
        try {
            return transcriptionRestClient.post()
                    .uri(baseUrl + "/api/v1/generate-reference")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
                    })
                    .body(PythonGenerateReferenceResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    public PythonEvaluateAudioResponse evaluateAudio(PythonEvaluateAudioRequest request) {
        try {
            return restClient.post()
                    .uri(baseUrl + "/api/v1/evaluate-audio")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
                    })
                    .body(PythonEvaluateAudioResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}

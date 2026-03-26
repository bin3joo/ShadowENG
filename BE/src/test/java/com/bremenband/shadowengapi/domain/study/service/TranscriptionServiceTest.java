package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonGenerateReferenceResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @InjectMocks
    private TranscriptionService transcriptionService;

    @Mock private PythonApiClient pythonApiClient;
    @Mock private S3Uploader s3Uploader;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    private static final String VIDEO_ID  = "dQw4w9WgXcQ";
    private static final double START_SEC = 30.0;
    private static final double END_SEC   = 60.0;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private PythonGenerateReferenceResponse successResponse(boolean translationSuccess) {
        var wt = new PythonGenerateReferenceResponse.WordTimestamp("I", 1.0, 1.3, 0.98);
        var features = new PythonGenerateReferenceResponse.PythonFeatures(
                List.of(120.0), List.of(0.03));
        var part = new PythonGenerateReferenceResponse.Part(
                "I got it bad.", 1.0, 3.5, 2.5,
                List.of(wt), features,
                "나 완전히 빠졌어.", List.of("bad"), "Normal", 3.5, List.of());
        return new PythonGenerateReferenceResponse("SUCCESS", translationSuccess, List.of(part));
    }

    private PythonGenerateReferenceResponse failResponse() {
        return new PythonGenerateReferenceResponse("FAIL", false, List.of());
    }

    // ── 정상 케이스 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("translation_success=true 이면 1회 호출로 결과 반환")
    void transcribe_success_singleCall() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(successResponse(true));
        given(s3Uploader.uploadJson(anyString(), anyString())).willReturn("s3://features/test.json");

        List<TranscribedSentence> result = transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC);

        assertThat(result).hasSize(1);
        then(pythonApiClient).should(times(1)).generateReference(anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("startSec/endSec 오프셋이 문장 시간에 더해진다")
    void transcribe_offsetAppliedToSentence() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(successResponse(true));
        given(s3Uploader.uploadJson(anyString(), anyString())).willReturn("s3://features/test.json");

        List<TranscribedSentence> result = transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC);

        TranscribedSentence sentence = result.get(0);
        assertThat(sentence.startSec()).isEqualTo(1.0 + START_SEC);  // 31.0
        assertThat(sentence.endSec()).isEqualTo(3.5 + START_SEC);    // 33.5
    }

    @Test
    @DisplayName("startSec 오프셋이 wordTimestamps에도 더해진다")
    void transcribe_offsetAppliedToWordTimestamps() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(successResponse(true));
        given(s3Uploader.uploadJson(anyString(), anyString())).willReturn("s3://features/test.json");

        List<TranscribedSentence> result = transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC);

        TranscribedSentence sentence = result.get(0);
        PythonGenerateReferenceResponse.WordTimestamp wt = sentence.wordTimestampList().get(0);
        assertThat(wt.start()).isEqualTo(1.0 + START_SEC);  // 31.0
        assertThat(wt.end()).isEqualTo(1.3 + START_SEC);    // 31.3
    }

    // ── 재시도 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("translation_success=false 이면 재시도 후 성공 시 결과 반환")
    void transcribe_retryOnTranslationFailure_thenSuccess() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(successResponse(false))
                .willReturn(successResponse(true));
        given(s3Uploader.uploadJson(anyString(), anyString())).willReturn("s3://features/test.json");

        List<TranscribedSentence> result = transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC);

        assertThat(result).hasSize(1);
        then(pythonApiClient).should(times(2)).generateReference(anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("translation_success=false가 MAX_RETRIES 초과 지속되면 EXTERNAL_API_ERROR 예외")
    void transcribe_allRetriesExhausted_throwsException() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(successResponse(false));

        assertThatThrownBy(() -> transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);

        // MAX_RETRIES=2 → 총 3회 호출
        then(pythonApiClient).should(times(3)).generateReference(anyString(), anyDouble(), anyDouble());
    }

    // ── 실패 케이스 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("status=FAIL 이면 재시도 없이 즉시 EXTERNAL_API_ERROR 예외")
    void transcribe_statusFail_throwsImmediately() {
        given(pythonApiClient.generateReference(anyString(), anyDouble(), anyDouble()))
                .willReturn(failResponse());

        assertThatThrownBy(() -> transcriptionService.transcribe(VIDEO_ID, START_SEC, END_SEC))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);

        then(pythonApiClient).should(times(1)).generateReference(anyString(), anyDouble(), anyDouble());
    }
}

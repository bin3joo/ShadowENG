package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.facade.StudyReportFacade;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.redis.PendingEvaluation;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @InjectMocks
    private EvaluationService evaluationService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private SentenceRepository     sentenceRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private PythonApiClient        pythonApiClient;
    @Mock private StudyReportFacade      studyReportFacade;
    @Mock private S3Uploader             s3Uploader;
    @Mock private PendingEvaluationStore pendingEvaluationStore;
    @Spy  private ObjectMapper           objectMapper;

    private static final Long   USER_ID       = 1L;
    private static final String FEATURES_JSON = "{\"f0_array\":[120.1],\"rms_array\":[0.03]}";

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private StudySession buildSession(Long sessionId) {
        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("Test").embedUrl("url")
                .thumbnailUrl("thumb").duration(212).channelTitle("Ch").build();
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p1").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0.0).endSec(60.0).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session) {
        Sentence sentence = Sentence.builder()
                .studySession(session)
                .content("I got it bad.")
                .startSec(5.61).endSec(10.78).durationSec(5.17)
                .wordTimestamps("[{\"word\":\"I\",\"start\":5.61,\"end\":5.9,\"score\":0.98}]")
                .featuresUrl("features/test.json")
                .build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        return sentence;
    }

    private PythonEvaluateAudioResponse buildSuccessPythonResponse() {
        List<PythonEvaluateAudioResponse.WordLevelFeedback> wordFeedback = List.of(
                new PythonEvaluateAudioResponse.WordLevelFeedback("I", "good", 5.61, 5.9, 5.62, 5.88));
        PythonEvaluateAudioResponse.BoundaryToneFeedback boundary =
                new PythonEvaluateAudioResponse.BoundaryToneFeedback(8.2, 6.1, "weak");
        PythonEvaluateAudioResponse.DynamicStressFeedback dynamic =
                new PythonEvaluateAudioResponse.DynamicStressFeedback(0.24, 0.2, "exaggerated");
        PythonEvaluateAudioResponse.Details details =
                new PythonEvaluateAudioResponse.Details(wordFeedback, boundary, dynamic, List.of());
        PythonEvaluateAudioResponse.Scores scores =
                new PythonEvaluateAudioResponse.Scores(73.7, 93.8, 37.6, 73.0, 55.8, 76.0, 85.2, 100.0);
        return new PythonEvaluateAudioResponse("SUCCESS", null, "I got it bad", details, scores);
    }

    private List<PendingEvaluation> buildAllPending() {
        String wlf = "[{\"word\":\"I\",\"status\":\"good\"}]";
        String btf = "{\"status\":\"good\"}";
        String dsf = "{\"status\":\"good\"}";
        return List.of(
                new PendingEvaluation(1, "t1", wlf, btf, dsf, 70.0, 90.0, 35.0, 70.0, 50.0, 70.0, 80.0, 100.0),
                new PendingEvaluation(2, "t2", wlf, btf, dsf, 72.0, 92.0, 36.0, 71.0, 52.0, 72.0, 82.0, 100.0),
                new PendingEvaluation(3, "t3", wlf, btf, dsf, 74.0, 94.0, 37.0, 73.0, 54.0, 74.0, 84.0, 100.0),
                new PendingEvaluation(4, "t4", wlf, btf, dsf, 73.7, 93.8, 37.6, 73.0, 55.8, 76.0, 85.2, 100.0)
        );
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "step {0}: Redis에만 저장하고 DB에는 저장하지 않는다")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("step 1~3: 평가 결과를 Redis에만 임시 저장하고 DB에 저장하지 않는다")
    void evaluate_step1to3_Redis에만저장(int step) {
        // given
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session  = buildSession(sessionId);
        Sentence     sentence = buildSentence(sentenceId, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any(PythonEvaluateAudioRequest.class)))
                .willReturn(buildSuccessPythonResponse());
        // step 3의 경우 이전 step(2) 완료 여부 확인
        if (step > 2) {
            given(pendingEvaluationStore.findStep(sessionId, sentenceId, step - 1))
                    .willReturn(Optional.of(buildAllPending().get(step - 2)));
        }

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "audio-bytes".getBytes());

        // when
        EvaluationResponse response =
                evaluationService.evaluate(sessionId, sentenceId, step, audio, USER_ID);

        // then — 응답은 정상 반환
        assertThat(response.sentenceId()).isEqualTo(sentenceId);
        assertThat(response.userTranscription()).isEqualTo("I got it bad");
        assertThat(response.scores().totalScore()).isEqualTo(73.7);

        // DB 저장 없음, Redis에만 저장
        then(pendingEvaluationStore).should(times(1))
                .save(eq(sessionId), eq(sentenceId), any(PendingEvaluation.class));
        then(evaluationRepository).should(never()).saveAll(any());
        then(studyReportFacade).should(never()).completeSessionAndAutoReport(any(), any());
    }

    @Test
    @DisplayName("step 4: 사이클 전체를 DB에 일괄 저장하고 세션 완료 여부를 체크한다")
    void evaluate_step4_전체사이클_DB저장_세션완료체크() {
        // given
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session  = buildSession(sessionId);
        Sentence     sentence = buildSentence(sentenceId, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildSuccessPythonResponse());
        given(pendingEvaluationStore.findStep(sessionId, sentenceId, 3))
                .willReturn(Optional.of(buildAllPending().get(2)));
        given(pendingEvaluationStore.findAll(sessionId, sentenceId)).willReturn(buildAllPending());

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "audio-bytes".getBytes());

        // when
        EvaluationResponse response =
                evaluationService.evaluate(sessionId, sentenceId, 4, audio, USER_ID);

        // then
        assertThat(response.sentenceId()).isEqualTo(sentenceId);

        // step 4: Redis 저장 → 전체 조회 → DB 일괄 저장 → Redis 삭제 → 세션 완료 체크
        then(pendingEvaluationStore).should(times(1))
                .save(eq(sessionId), eq(sentenceId), any(PendingEvaluation.class));
        then(pendingEvaluationStore).should(times(1)).findAll(sessionId, sentenceId);
        then(evaluationRepository).should(times(1)).saveAll(any());
        then(pendingEvaluationStore).should(times(1)).deleteAll(sessionId, sentenceId);
        then(studyReportFacade).should(times(1)).completeSessionAndAutoReport(sessionId, sentenceId);
    }

    @Test
    @DisplayName("세션이 존재하지 않으면 SESSION_NOT_FOUND 예외를 던진다")
    void evaluate_세션없음_예외() {
        Long sessionId  = 999L;
        Long sentenceId = 10L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);

        then(evaluationRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("문장이 존재하지 않으면 SENTENCE_NOT_FOUND 예외를 던진다")
    void evaluate_문장없음_예외() {
        Long sessionId  = 1L;
        Long sentenceId = 999L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId)));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SENTENCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Python API가 FAIL을 반환하면 VOICE_RECOGNITION_FAILED 예외를 던진다")
    void evaluate_음성인식실패_예외() {
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session  = buildSession(sessionId);
        Sentence     sentence = buildSentence(sentenceId, session);
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any()))
                .willReturn(new PythonEvaluateAudioResponse("FAIL",
                        "음성이 인식되지 않았습니다. 다시 녹음해주세요.", null, null, null));

        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VOICE_RECOGNITION_FAILED);

        then(s3Uploader).should(never()).upload(any());
        then(s3Uploader).should(never()).delete(any());
        then(pendingEvaluationStore).should(never()).save(any(), any(), any());
        then(evaluationRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("음성 파일은 S3에 업로드하지 않고 Base64로 인코딩하여 Python API에 전달한다")
    void evaluate_audioEncodedAsBase64_S3업로드없음() throws Exception {
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session  = buildSession(sessionId);
        Sentence     sentence = buildSentence(sentenceId, session);
        byte[] audioBytes = "audio-bytes-content".getBytes();
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.webm", "audio/webm", audioBytes);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(s3Uploader.fetchJson(any())).willReturn(FEATURES_JSON);
        given(pythonApiClient.evaluateAudio(any())).willReturn(buildSuccessPythonResponse());

        evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID);

        // S3 업로드/삭제 없음
        then(s3Uploader).should(never()).upload(any());
        then(s3Uploader).should(never()).delete(any());

        // Python에 Base64 인코딩된 바이트가 전달되었는지 검증
        ArgumentCaptor<PythonEvaluateAudioRequest> captor =
                ArgumentCaptor.forClass(PythonEvaluateAudioRequest.class);
        then(pythonApiClient).should(times(1)).evaluateAudio(captor.capture());
        PythonEvaluateAudioRequest captured = captor.getValue();

        byte[] decoded = Base64.getDecoder().decode(captured.userAudio());
        assertThat(decoded).isEqualTo(audioBytes);
        assertThat(captured.userAudioFormat()).isEqualTo("webm");
    }

    @Test
    @DisplayName("문장이 해당 세션 소속이 아니면 INVALID_REQUEST 예외를 던진다")
    void evaluate_다른세션문장_예외() {
        Long sessionId      = 1L;
        Long otherSessionId = 2L;
        Long sentenceId     = 10L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        StudySession session      = buildSession(sessionId);
        StudySession otherSession = buildSession(otherSessionId);
        Sentence sentence         = buildSentence(sentenceId, otherSession); // 다른 세션 소속

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));

        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}

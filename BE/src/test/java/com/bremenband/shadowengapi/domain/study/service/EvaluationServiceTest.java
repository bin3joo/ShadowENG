package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.domain.study.dto.res.EvaluationResponse;
import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @InjectMocks
    private EvaluationService evaluationService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private SentenceRepository sentenceRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private PythonApiClient pythonApiClient;
    @Spy  private ObjectMapper objectMapper;
    @Mock private StudySessionWriter studySessionWriter;

    private static final Long USER_ID = 1L;

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
                .features("{\"f0_array\":[120.1],\"rms_array\":[0.03]}")
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

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 요청 시 평가 결과를 DB에 저장하고 응답을 반환한다")
    void evaluate_성공() {
        // given
        Long sessionId = 1L;
        Long sentenceId = 10L;
        StudySession session = buildSession(sessionId);
        Sentence sentence   = buildSentence(sentenceId, session);

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(pythonApiClient.evaluateAudio(any(PythonEvaluateAudioRequest.class)))
                .willReturn(buildSuccessPythonResponse());
        given(evaluationRepository.save(any(Evaluation.class))).willAnswer(inv -> inv.getArgument(0));

        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "audio-bytes".getBytes());

        // when
        EvaluationResponse response = evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID);

        // then
        assertThat(response.sentenceId()).isEqualTo(sentenceId);
        assertThat(response.startSec()).isEqualTo(5.61);
        assertThat(response.endSec()).isEqualTo(10.78);
        assertThat(response.durationSec()).isEqualTo(5.17);
        assertThat(response.userTranscription()).isEqualTo("I got it bad");

        assertThat(response.details().wordLevelFeedback()).hasSize(1);
        assertThat(response.details().wordLevelFeedback().get(0).word()).isEqualTo("I");
        assertThat(response.details().wordLevelFeedback().get(0).status()).isEqualTo("good");

        assertThat(response.details().boundaryToneFeedback().status()).isEqualTo("weak");

        assertThat(response.details().dynamicStressFeedback().status()).isEqualTo("exaggerated");

        assertThat(response.scores().totalScore()).isEqualTo(73.7);
        assertThat(response.scores().wordAccuracy()).isEqualTo(93.8);

        then(evaluationRepository).should(times(1)).save(any(Evaluation.class));
        then(studySessionWriter).should(times(1)).completeSessionIfAllEvaluated(sessionId);
    }

    @Test
    @DisplayName("세션이 존재하지 않으면 SESSION_NOT_FOUND 예외를 던진다")
    void evaluate_세션없음_예외() {
        // given
        Long sessionId = 999L;
        Long sentenceId = 10L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);

        then(evaluationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("문장이 존재하지 않으면 SENTENCE_NOT_FOUND 예외를 던진다")
    void evaluate_문장없음_예외() {
        // given
        Long sessionId = 1L;
        Long sentenceId = 999L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId)));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SENTENCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Python API가 FAIL을 반환하면 VOICE_RECOGNITION_FAILED 예외를 던진다")
    void evaluate_음성인식실패_예외() {
        // given
        Long sessionId = 1L;
        Long sentenceId = 10L;
        StudySession session = buildSession(sessionId);
        Sentence sentence   = buildSentence(sentenceId, session);
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(pythonApiClient.evaluateAudio(any()))
                .willReturn(new PythonEvaluateAudioResponse("FAIL",
                        "음성이 인식되지 않았습니다. 다시 녹음해주세요.", null, null, null));

        // when & then
        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VOICE_RECOGNITION_FAILED);

        then(evaluationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("문장이 해당 세션 소속이 아니면 INVALID_REQUEST 예외를 던진다")
    void evaluate_다른세션문장_예외() {
        // given
        Long sessionId = 1L;
        Long otherSessionId = 2L;
        Long sentenceId = 10L;
        MockMultipartFile audio = new MockMultipartFile(
                "file", "test.wav", "audio/wav", "bytes".getBytes());

        StudySession session      = buildSession(sessionId);
        StudySession otherSession = buildSession(otherSessionId);
        Sentence sentence         = buildSentence(sentenceId, otherSession); // 다른 세션 소속

        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));

        // when & then
        assertThatThrownBy(() -> evaluationService.evaluate(sessionId, sentenceId, 1, audio, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}

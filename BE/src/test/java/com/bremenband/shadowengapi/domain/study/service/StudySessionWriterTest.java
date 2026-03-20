package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudySessionWriterTest {

    @InjectMocks
    private StudySessionWriter studySessionWriter;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private SentenceRepository sentenceRepository;
    @Mock private EvaluationRepository evaluationRepository;

    private StudySession buildSession(Long sessionId) {
        Video video = Video.builder()
                .videoId("dQw4w9WgXcQ").title("Test").embedUrl("url")
                .thumbnailUrl("thumb").duration(212).channelTitle("Ch").build();
        User user = User.builder()
                .email("u@e.com").nickname("nick").provider("KAKAO").providerId("p1").build();
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0.0).endSec(60.0).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Sentence buildSentence(Long sentenceId, StudySession session, int studyCount) {
        Sentence sentence = Sentence.builder()
                .studySession(session)
                .content("Test sentence.")
                .startSec(0.0).endSec(5.0).durationSec(5.0)
                .wordTimestamps("[]")
                .featuresUrl(null)
                .build();
        ReflectionTestUtils.setField(sentence, "id", sentenceId);
        ReflectionTestUtils.setField(sentence, "studyCount", studyCount);
        return sentence;
    }

    @Test
    @DisplayName("모든 문장의 studyCount > cycleCount이면 세션이 COMPLETED로 전환된다")
    void completeSessionIfAllEvaluated_전체평가완료_COMPLETED() {
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session = buildSession(sessionId);         // cycleCount=0

        // sentenceId=10 → studyCount=0 → 증가 후 1 (> cycleCount=0)
        Sentence target = buildSentence(sentenceId, session, 0);
        // 다른 문장은 이미 studyCount=1
        Sentence other  = buildSentence(11L, session, 1);

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(target));
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findByStudySession_Id(sessionId)).willReturn(List.of(target, other));

        studySessionWriter.completeSessionIfAllEvaluated(sessionId, sentenceId);

        // target.studyCount → 1(증가), other.studyCount=1, cycleCount=0 → 모두 1>0 → COMPLETED
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("studyCount <= cycleCount인 문장이 있으면 세션은 ACTIVE를 유지한다")
    void completeSessionIfAllEvaluated_미완료문장존재_ACTIVE() {
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session = buildSession(sessionId);          // cycleCount=0

        Sentence target = buildSentence(sentenceId, session, 0); // 증가 후 studyCount=1
        Sentence other  = buildSentence(11L, session, 0);        // studyCount=0 (미학습)

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(target));
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findByStudySession_Id(sessionId)).willReturn(List.of(target, other));

        studySessionWriter.completeSessionIfAllEvaluated(sessionId, sentenceId);

        // other.studyCount=0, cycleCount=0 → 0>0 false → ACTIVE 유지
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("세션에 문장이 하나도 없으면 상태가 변경되지 않는다")
    void completeSessionIfAllEvaluated_문장없음_ACTIVE() {
        Long sessionId  = 1L;
        Long sentenceId = 10L;
        StudySession session  = buildSession(sessionId);
        Sentence     sentence = buildSentence(sentenceId, session, 0);

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(sentenceRepository.findByStudySession_Id(sessionId)).willReturn(List.of());

        studySessionWriter.completeSessionIfAllEvaluated(sessionId, sentenceId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("완료 처리 시 세션이 존재하지 않으면 SESSION_NOT_FOUND 예외를 던진다")
    void completeSessionIfAllEvaluated_세션없음_예외() {
        Long sessionId  = 999L;
        Long sentenceId = 10L;
        StudySession dummySession = buildSession(1L);
        Sentence     sentence     = buildSentence(sentenceId, dummySession, 0);

        given(sentenceRepository.findById(sentenceId)).willReturn(Optional.of(sentence));
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studySessionWriter.completeSessionIfAllEvaluated(sessionId, sentenceId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }
}

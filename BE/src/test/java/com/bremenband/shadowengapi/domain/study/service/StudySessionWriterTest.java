package com.bremenband.shadowengapi.domain.study.service;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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

    @Test
    @DisplayName("모든 문장이 평가되면 세션 상태가 COMPLETED로 변경된다")
    void completeSessionIfAllEvaluated_전체평가완료_COMPLETED() {
        // given
        Long sessionId = 1L;
        StudySession session = buildSession(sessionId);

        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(3L);
        given(evaluationRepository.countDistinctEvaluatedSentencesBySessionId(sessionId)).willReturn(3L);
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.of(session));

        // when
        studySessionWriter.completeSessionIfAllEvaluated(sessionId);

        // then
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("평가되지 않은 문장이 남아있으면 세션 상태가 ACTIVE를 유지한다")
    void completeSessionIfAllEvaluated_미완료문장존재_ACTIVE() {
        // given
        Long sessionId = 1L;

        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(3L);
        given(evaluationRepository.countDistinctEvaluatedSentencesBySessionId(sessionId)).willReturn(2L);

        // when
        studySessionWriter.completeSessionIfAllEvaluated(sessionId);

        // then
        then(studySessionRepository).should(never()).findById(sessionId);
    }

    @Test
    @DisplayName("문장이 하나도 없는 세션은 COMPLETED로 전환되지 않는다")
    void completeSessionIfAllEvaluated_문장없음_ACTIVE() {
        // given
        Long sessionId = 1L;

        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(0L);
        given(evaluationRepository.countDistinctEvaluatedSentencesBySessionId(sessionId)).willReturn(0L);

        // when
        studySessionWriter.completeSessionIfAllEvaluated(sessionId);

        // then
        then(studySessionRepository).should(never()).findById(sessionId);
    }

    @Test
    @DisplayName("완료 처리 시 세션이 존재하지 않으면 SESSION_NOT_FOUND 예외를 던진다")
    void completeSessionIfAllEvaluated_세션없음_예외() {
        // given
        Long sessionId = 999L;

        given(sentenceRepository.countByStudySession_Id(sessionId)).willReturn(2L);
        given(evaluationRepository.countDistinctEvaluatedSentencesBySessionId(sessionId)).willReturn(2L);
        given(studySessionRepository.findById(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> studySessionWriter.completeSessionIfAllEvaluated(sessionId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }
}

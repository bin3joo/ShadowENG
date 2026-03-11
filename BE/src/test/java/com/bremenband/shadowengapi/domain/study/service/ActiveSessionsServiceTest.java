package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.res.ActiveSessionsResponse;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.youtube.repository.VideoRepository;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ActiveSessionsServiceTest {

    @InjectMocks
    private StudySessionService studySessionService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private VideoRepository        videoRepository;
    @Mock private SentenceRepository     sentenceRepository;
    @Mock private EvaluationRepository   evaluationRepository;
    @Mock private UserRepository         userRepository;
    @Mock private YoutubeService         youtubeService;
    @Mock private TranscriptionService   transcriptionService;

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    private StudySession buildSession(Long sessionId) {
        Video video = Video.builder()
                .videoId(VIDEO_ID)
                .title("Never Gonna Give You Up")
                .embedUrl("https://www.youtube.com/embed/" + VIDEO_ID)
                .thumbnailUrl("https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg")
                .duration(212).channelTitle("Rick Astley")
                .build();

        User user = User.builder()
                .email("user@example.com").nickname("브레맨")
                .provider("KAKAO").providerId("kakao-123").build();

        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0.0).endSec(60.0).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    @Test
    @DisplayName("ACTIVE 세션이 여러 개 있으면 전체 문장 수와 학습 완료 문장 수를 포함한 목록을 반환한다")
    void getActiveSessions_세션여러개_전체반환() {
        // given
        Long userId = 1L;
        StudySession session1 = buildSession(100L);
        StudySession session2 = buildSession(200L);
        List<Long> sessionIds = List.of(100L, 200L);

        given(studySessionRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE))
                .willReturn(List.of(session1, session2));
        given(sentenceRepository.countMapBySessionIds(sessionIds))
                .willReturn(Map.of(100L, 8L, 200L, 6L));
        given(evaluationRepository.completedSentencesMapBySessionIds(sessionIds))
                .willReturn(Map.of(100L, 5L, 200L, 2L));

        // when
        ActiveSessionsResponse response = studySessionService.getActiveSessions(userId);

        // then
        assertThat(response.activeSessions()).hasSize(2);

        assertThat(response.activeSessions().get(0).sessionId()).isEqualTo(100L);
        assertThat(response.activeSessions().get(0).totalSentences()).isEqualTo(8L);
        assertThat(response.activeSessions().get(0).completedSentences()).isEqualTo(5L);
        assertThat(response.activeSessions().get(0).thumbnails().url())
                .isEqualTo("https://i.ytimg.com/vi/" + VIDEO_ID + "/sddefault.jpg");

        assertThat(response.activeSessions().get(1).sessionId()).isEqualTo(200L);
        assertThat(response.activeSessions().get(1).totalSentences()).isEqualTo(6L);
        assertThat(response.activeSessions().get(1).completedSentences()).isEqualTo(2L);

        then(studySessionRepository).should(times(1))
                .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);
        then(sentenceRepository).should(times(1)).countMapBySessionIds(sessionIds);
        then(evaluationRepository).should(times(1)).completedSentencesMapBySessionIds(sessionIds);
    }

    @Test
    @DisplayName("ACTIVE 세션이 없으면 빈 목록을 반환한다")
    void getActiveSessions_세션없음_빈목록반환() {
        // given
        Long userId = 1L;

        given(studySessionRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE))
                .willReturn(List.of());

        // when
        ActiveSessionsResponse response = studySessionService.getActiveSessions(userId);

        // then
        assertThat(response.activeSessions()).isEmpty();

        then(studySessionRepository).should(times(1))
                .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);
        then(sentenceRepository).should(times(0)).countMapBySessionIds(List.of());
    }
}

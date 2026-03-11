package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.study.repository.EvaluationRepository;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
<<<<<<< Updated upstream
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
=======
>>>>>>> Stashed changes
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StudySessionServiceTest {

    @InjectMocks
    private StudySessionService studySessionService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private SentenceRepository     sentenceRepository;
    @Mock private EvaluationRepository   evaluationRepository;

    @Test
<<<<<<< Updated upstream
    @DisplayName("ACTIVE 세션이 존재하면 썸네일, 영상 제목, 문장 진행 현황을 반환한다")
    void getRecentSession_ACTIVE세션존재_세션반환() {
        // given
        Long userId  = 1L;
=======
    @DisplayName("ACTIVE 세션이 존재하면 썸네일 URL, 영상 제목, 문장 진행 현황을 반환한다")
    void getRecentSession_ACTIVE세션존재_세션반환() {
        // given
        Long userId   = 1L;
>>>>>>> Stashed changes
        String videoId = "dQw4w9WgXcQ";

        User user = User.builder()
                .email("user@example.com").nickname("브레맨")
                .provider("KAKAO").providerId("kakao-123")
                .build();

        Video video = Video.builder()
                .videoId(videoId)
                .title("Never Gonna Give You Up")
                .embedUrl("https://www.youtube.com/embed/" + videoId)
                .thumbnailUrl("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg")
                .duration(212).channelTitle("Rick Astley")
                .build();

        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(0.0).endSec(60.0).build();
        ReflectionTestUtils.setField(session, "id", 12345L);

        given(studySessionRepository.findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE))
                .willReturn(Optional.of(session));
        given(sentenceRepository.countByStudySession_Id(12345L)).willReturn(8L);
        given(evaluationRepository.countDistinctEvaluatedSentencesBySessionId(12345L)).willReturn(5L);

        // when
        RecentStudySessionResponse response = studySessionService.getRecentSession(userId);

        // then
        assertThat(response.latestActiveSession()).isNotNull();
        assertThat(response.latestActiveSession().sessionId()).isEqualTo(12345L);
        assertThat(response.latestActiveSession().thumbnailUrl())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg");
        assertThat(response.latestActiveSession().videoTitle()).isEqualTo("Never Gonna Give You Up");
        assertThat(response.latestActiveSession().totalSentences()).isEqualTo(8L);
        assertThat(response.latestActiveSession().completedSentences()).isEqualTo(5L);

        then(studySessionRepository).should(times(1))
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);
        then(sentenceRepository).should(times(1)).countByStudySession_Id(12345L);
        then(evaluationRepository).should(times(1)).countDistinctEvaluatedSentencesBySessionId(12345L);
    }

    @Test
    @DisplayName("ACTIVE 세션이 없으면 latestActiveSession이 null인 응답을 반환한다")
    void getRecentSession_ACTIVE세션없음_null반환() {
        // given
        Long userId = 1L;

        given(studySessionRepository.findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE))
                .willReturn(Optional.empty());

        // when
        RecentStudySessionResponse response = studySessionService.getRecentSession(userId);

        // then
        assertThat(response.latestActiveSession()).isNull();

        then(studySessionRepository).should(times(1))
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);
    }
}

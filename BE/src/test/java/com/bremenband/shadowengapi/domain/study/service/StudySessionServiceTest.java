package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.res.RecentStudySessionResponse;
import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
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

    @Mock
    private StudySessionRepository studySessionRepository;

    @Test
    @DisplayName("ACTIVE 세션이 존재하면 썸네일과 진행률을 포함한 최근 세션을 반환한다")
    void getRecentSession_ACTIVE세션존재_세션반환() {
        // given
        Long userId = 1L;
        String videoId = "dQw4w9WgXcQ";

        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();

        Video video = Video.builder()
                .videoId(videoId)
                .title("Never Gonna Give You Up")
                .embedUrl("https://www.youtube.com/embed/" + videoId)
                .thumbnailUrl("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg")
                .duration(212)
                .channelTitle("Rick Astley")
                .build();

        StudySession session = StudySession.builder()
                .video(video)
                .user(user)
                .startSec(0.0)
                .endSec(60.0)
                .build();
        ReflectionTestUtils.setField(session, "id", 12345L);
        ReflectionTestUtils.setField(session, "progressRate", 40);

        given(studySessionRepository.findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE))
                .willReturn(Optional.of(session));

        // when
        RecentStudySessionResponse response = studySessionService.getRecentSession(userId);

        // then
        assertThat(response.latestActiveSession()).isNotNull();
        assertThat(response.latestActiveSession().sessionId()).isEqualTo(12345L);
        assertThat(response.latestActiveSession().progressRate()).isEqualTo(40);

        assertThat(response.latestActiveSession().thumbnails().defaultThumbnail().url())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/default.jpg");
        assertThat(response.latestActiveSession().thumbnails().defaultThumbnail().width()).isEqualTo(120);
        assertThat(response.latestActiveSession().thumbnails().defaultThumbnail().height()).isEqualTo(90);

        assertThat(response.latestActiveSession().thumbnails().medium().url())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
        assertThat(response.latestActiveSession().thumbnails().high().url())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
        assertThat(response.latestActiveSession().thumbnails().standard().url())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg");
        assertThat(response.latestActiveSession().thumbnails().maxres().url())
                .isEqualTo("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg");
        assertThat(response.latestActiveSession().thumbnails().maxres().width()).isEqualTo(1280);
        assertThat(response.latestActiveSession().thumbnails().maxres().height()).isEqualTo(720);

        then(studySessionRepository).should(times(1))
                .findTopByUser_IdAndStatusOrderByCreatedAtDesc(userId, SessionStatus.ACTIVE);
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

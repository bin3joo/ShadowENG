package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.req.StudySessionCreateRequest;
import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
import com.bremenband.shadowengapi.domain.study.repository.SentenceRepository;
import com.bremenband.shadowengapi.domain.study.repository.StudySessionRepository;
import com.bremenband.shadowengapi.domain.youtube.repository.VideoRepository;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StudySessionCreateServiceTest {

    @InjectMocks
    private StudySessionService studySessionService;

    @Mock private StudySessionRepository studySessionRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private SentenceRepository sentenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private YoutubeService youtubeService;
    @Mock private TranscriptionService transcriptionService;

    private static final String EMBED_URL = "https://www.youtube.com/embed/dQw4w9WgXcQ";
    private static final String VIDEO_ID  = "dQw4w9WgXcQ";

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private Video buildVideo() {
        Video video = Video.builder()
                .videoId(VIDEO_ID)
                .title("Never Gonna Give You Up")
                .embedUrl(EMBED_URL)
                .thumbnailUrl("https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg")
                .duration(212)
                .channelTitle("Rick Astley")
                .build();
        return video;
    }

    private User buildUser(Long userId) {
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private StudySession buildSession(Long sessionId, Video video, User user) {
        StudySession session = StudySession.builder()
                .video(video).user(user).startSec(15.5).endSec(45.0).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Video가 DB에 없으면 YouTube API를 호출해 저장한 뒤 세션을 생성한다")
    void createStudySession_비디오없음_YouTube에서저장후세션생성() {
        // given
        Long userId = 1L;
        StudySessionCreateRequest request =
                new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        Video video   = buildVideo();
        User user     = buildUser(userId);
        StudySession session = buildSession(12345L, video, user);

        VideoInfoResponse youtubeInfo = new VideoInfoResponse(
                VIDEO_ID, EMBED_URL, "Never Gonna Give You Up",
                "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg", 212L, "Rick Astley");

        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.empty());
        given(youtubeService.getVideo(EMBED_URL)).willReturn(youtubeInfo);
        given(videoRepository.save(any(Video.class))).willReturn(video);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(studySessionRepository.save(any(StudySession.class))).willReturn(session);
        given(transcriptionService.transcribe(VIDEO_ID, 15.5, 45.0)).willReturn(List.of(
                new TranscribedSentence("Hello world", 15.5, 20.0, 4.5,
                        "[{\"word\":\"Hello\",\"start\":15.5,\"end\":16.0,\"score\":0.98}]",
                        "{\"f0_array\":[120.1],\"rms_array\":[0.03]}")
        ));

        Sentence sentence = Sentence.builder()
                .studySession(session).content("Hello world")
                .startSec(15.5).endSec(20.0).durationSec(4.5).build();
        ReflectionTestUtils.setField(sentence, "id", 1L);
        given(sentenceRepository.save(any(Sentence.class))).willReturn(sentence);

        // when
        StudySessionCreateResponse response = studySessionService.createStudySession(userId, request);

        // then
        assertThat(response.sessionId()).isEqualTo(12345L);
        assertThat(response.videoData().videoId()).isEqualTo(VIDEO_ID);
        assertThat(response.videoData().title()).isEqualTo("Never Gonna Give You Up");
        assertThat(response.videoData().duration()).isEqualTo(212L);
        assertThat(response.sentencesData()).hasSize(1);
        assertThat(response.sentencesData().get(0).sentence()).isEqualTo("Hello world");
        assertThat(response.sentencesData().get(0).studyCount()).isEqualTo(0);

        then(youtubeService).should(times(1)).getVideo(EMBED_URL);
        then(videoRepository).should(times(1)).save(any(Video.class));
        then(studySessionRepository).should(times(1)).save(any(StudySession.class));
        then(sentenceRepository).should(times(1)).save(any(Sentence.class));
    }

    @Test
    @DisplayName("Video가 DB에 이미 있으면 YouTube API를 호출하지 않고 세션을 생성한다")
    void createStudySession_비디오있음_YouTube호출없이세션생성() {
        // given
        Long userId = 1L;
        StudySessionCreateRequest request =
                new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        Video video   = buildVideo();
        User user     = buildUser(userId);
        StudySession session = buildSession(12345L, video, user);

        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.of(video));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(studySessionRepository.save(any(StudySession.class))).willReturn(session);
        given(transcriptionService.transcribe(VIDEO_ID, 15.5, 45.0)).willReturn(List.of());

        // when
        StudySessionCreateResponse response = studySessionService.createStudySession(userId, request);

        // then
        assertThat(response.sessionId()).isEqualTo(12345L);
        assertThat(response.sentencesData()).isEmpty();

        then(youtubeService).should(never()).getVideo(any());
    }

    @Test
    @DisplayName("존재하지 않는 userId로 요청하면 USER_NOT_FOUND 예외를 던진다")
    void createStudySession_사용자없음_예외() {
        // given
        Long userId = 999L;
        StudySessionCreateRequest request =
                new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.of(buildVideo()));
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> studySessionService.createStudySession(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(studySessionRepository).should(never()).save(any());
    }
}

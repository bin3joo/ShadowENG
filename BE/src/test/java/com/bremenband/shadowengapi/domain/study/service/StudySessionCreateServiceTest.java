package com.bremenband.shadowengapi.domain.study.service;

import com.bremenband.shadowengapi.domain.study.dto.req.StudySessionCreateRequest;
import com.bremenband.shadowengapi.domain.study.dto.res.StudySessionCreateResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.study.dto.transcription.TranscribedSentence;
import com.bremenband.shadowengapi.domain.youtube.entity.Video;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StudySessionCreateServiceTest {

    @InjectMocks
    private StudySessionService studySessionService;

    @Mock private VideoRepository videoRepository;
    @Mock private UserRepository userRepository;
    @Mock private YoutubeService youtubeService;
    @Mock private TranscriptionService transcriptionService;
    @Mock private StudySessionWriter studySessionWriter;

    private static final String EMBED_URL = "https://www.youtube.com/embed/dQw4w9WgXcQ";
    private static final String VIDEO_ID  = "dQw4w9WgXcQ";

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private Video buildVideo() {
        return Video.builder()
                .videoId(VIDEO_ID)
                .title("Never Gonna Give You Up")
                .embedUrl(EMBED_URL)
                .thumbnailUrl("https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg")
                .duration(212)
                .channelTitle("Rick Astley")
                .build();
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

    private StudySessionCreateResponse buildExpectedResponse() {
        return new StudySessionCreateResponse(
                12345L,
                null,
                new StudySessionCreateResponse.VideoData(
                        VIDEO_ID, EMBED_URL, "https://www.youtube.com/watch?v=" + VIDEO_ID,
                        "Never Gonna Give You Up",
                        "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg", 212L, "Rick Astley"),
                List.of(new StudySessionCreateResponse.SentenceData(
                        1L, 1, "Hello world", 15.5, 20.0, 4.5, 0,
                        null, List.of(), null, null, List.of(), List.of())),
                false
        );
    }

    // ── 테스트 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Video가 DB에 없으면 YouTube API를 호출해 저장한 뒤 세션을 생성한다")
    void createStudySession_비디오없음_YouTube에서저장후세션생성() {
        // given
        Long userId = 1L;
        StudySessionCreateRequest request = new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        Video video = buildVideo();
        User user   = buildUser(userId);
        VideoInfoResponse youtubeInfo = new VideoInfoResponse(
                VIDEO_ID, EMBED_URL, "https://www.youtube.com/watch?v=" + VIDEO_ID,
                "Never Gonna Give You Up",
                "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg", 212L, "Rick Astley");

        given(youtubeService.extractVideoId(EMBED_URL)).willReturn(VIDEO_ID);
        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.empty());
        given(youtubeService.getVideo(EMBED_URL)).willReturn(youtubeInfo);
        given(videoRepository.save(any(Video.class))).willReturn(video);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transcriptionService.transcribe(VIDEO_ID, 15.5, 45.0)).willReturn(List.of(
                new TranscribedSentence("Hello world", 15.5, 20.0, 4.5,
                        "[{\"word\":\"Hello\",\"start\":15.5,\"end\":16.0,\"score\":0.98}]",
                        "features/test.json",
                        null, List.of(), null, null, List.of(), List.of())
        ));
        given(studySessionWriter.saveSessionAndSentences(any(), any(), anyDouble(), anyDouble(), anyList()))
                .willReturn(buildExpectedResponse());

        // when
        StudySessionCreateResponse response = studySessionService.createStudySession(userId, request);

        // then
        assertThat(response.sessionId()).isEqualTo(12345L);
        assertThat(response.videoData().videoId()).isEqualTo(VIDEO_ID);
        assertThat(response.sentencesData()).hasSize(1);
        assertThat(response.sentencesData().get(0).sentence()).isEqualTo("Hello world");

        then(youtubeService).should(times(1)).getVideo(EMBED_URL);
        then(videoRepository).should(times(1)).save(any(Video.class));
        then(studySessionWriter).should(times(1))
                .saveSessionAndSentences(any(), any(), anyDouble(), anyDouble(), anyList());
    }

    @Test
    @DisplayName("Video가 DB에 이미 있으면 YouTube API를 호출하지 않고 세션을 생성한다")
    void createStudySession_비디오있음_YouTube호출없이세션생성() {
        // given
        Long userId = 1L;
        StudySessionCreateRequest request = new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        Video video = buildVideo();
        User user   = buildUser(userId);

        given(youtubeService.extractVideoId(EMBED_URL)).willReturn(VIDEO_ID);
        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.of(video));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transcriptionService.transcribe(VIDEO_ID, 15.5, 45.0)).willReturn(List.of());
        given(studySessionWriter.saveSessionAndSentences(any(), any(), anyDouble(), anyDouble(), anyList()))
                .willReturn(new StudySessionCreateResponse(12345L, null,
                        new StudySessionCreateResponse.VideoData(
                                VIDEO_ID, EMBED_URL, "https://www.youtube.com/watch?v=" + VIDEO_ID,
                                "Never Gonna Give You Up",
                                "https://i.ytimg.com/vi/" + VIDEO_ID + "/maxresdefault.jpg", 212L, "Rick Astley"),
                        List.of(), false));

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
        StudySessionCreateRequest request = new StudySessionCreateRequest(EMBED_URL, 15.5, 45.0);

        given(youtubeService.extractVideoId(EMBED_URL)).willReturn(VIDEO_ID);
        given(videoRepository.findById(VIDEO_ID)).willReturn(Optional.of(buildVideo()));
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> studySessionService.createStudySession(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(studySessionWriter).should(never()).saveSessionAndSentences(any(), any(), anyDouble(), anyDouble(), any());
    }
}

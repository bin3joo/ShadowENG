package com.bremenband.shadowengapi.domain.youtube.service;

import com.bremenband.shadowengapi.domain.youtube.client.YoutubeApiClient;
import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.youtube.dto.YoutubeApiResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class YoutubeServiceTest {

    @InjectMocks
    private YoutubeService youtubeService;

    @Mock
    private YoutubeApiClient youtubeApiClient;

    // ── URL 파싱 테스트 ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0} → videoId={1}")
    @DisplayName("다양한 유튜브 URL 형식에서 videoId를 올바르게 추출한다")
    @CsvSource({
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ,                dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ,   dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s, dQw4w9WgXcQ"
    })
    void extractVideoId_다양한URL_videoId추출(String url, String expectedVideoId) {
        // given & when
        String videoId = youtubeService.extractVideoId(url.trim());

        // then
        assertThat(videoId).isEqualTo(expectedVideoId.trim());
    }

    @Test
    @DisplayName("유튜브 URL이 아니면 INVALID_YOUTUBE_URL 예외를 던진다")
    void extractVideoId_잘못된URL_예외() {
        // given
        String invalidUrl = "https://www.naver.com/watch?v=dQw4w9WgXcQ";

        // when & then
        assertThatThrownBy(() -> youtubeService.extractVideoId(invalidUrl))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_YOUTUBE_URL);
    }

    // ── getVideo 통합 흐름 테스트 ────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 URL로 요청하면 영상 정보를 반환한다")
    void getVideo_유효한URL_영상정보반환() {
        // given
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String videoId = "dQw4w9WgXcQ";

        YoutubeApiResponse apiResponse = new YoutubeApiResponse(List.of(
                new YoutubeApiResponse.YoutubeItem(
                        videoId,
                        new YoutubeApiResponse.YoutubeSnippet(
                                "Rick Astley - Never Gonna Give You Up",
                                "Rick Astley",
                                new YoutubeApiResponse.YoutubeThumbnails(
                                        new YoutubeApiResponse.YoutubeThumbnailInfo(
                                                "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg", 1280, 720),
                                        null, null, null, null
                                )
                        ),
                        new YoutubeApiResponse.YoutubeContentDetails("PT3M32S")
                )
        ));

        given(youtubeApiClient.fetchVideoInfo(videoId)).willReturn(apiResponse);

        // when
        VideoInfoResponse response = youtubeService.getVideo(url);

        // then
        assertThat(response.videoId()).isEqualTo(videoId);
        assertThat(response.embedUrl()).isEqualTo("https://www.youtube.com/embed/" + videoId);
        assertThat(response.title()).isEqualTo("Rick Astley - Never Gonna Give You Up");
        assertThat(response.thumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg");
        assertThat(response.duration()).isEqualTo(212L);  // PT3M32S = 3*60+32 = 212
        assertThat(response.channelTitle()).isEqualTo("Rick Astley");

        then(youtubeApiClient).should(times(1)).fetchVideoInfo(videoId);
    }

    @Test
    @DisplayName("YouTube API가 빈 items를 반환하면 VIDEO_NOT_FOUND 예외를 던진다")
    void getVideo_영상없음_예외() {
        // given
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String videoId = "dQw4w9WgXcQ";

        given(youtubeApiClient.fetchVideoInfo(videoId))
                .willReturn(new YoutubeApiResponse(List.of()));

        // when & then
        assertThatThrownBy(() -> youtubeService.getVideo(url))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VIDEO_NOT_FOUND);
    }

    @Test
    @DisplayName("maxres 썸네일이 없으면 standard 썸네일 URL을 사용한다")
    void getVideo_maxres없음_standard썸네일사용() {
        // given
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String videoId = "dQw4w9WgXcQ";
        String standardUrl = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";

        YoutubeApiResponse apiResponse = new YoutubeApiResponse(List.of(
                new YoutubeApiResponse.YoutubeItem(
                        videoId,
                        new YoutubeApiResponse.YoutubeSnippet(
                                "Test Video",
                                "Test Channel",
                                new YoutubeApiResponse.YoutubeThumbnails(
                                        null,
                                        new YoutubeApiResponse.YoutubeThumbnailInfo(standardUrl, 640, 480),
                                        null, null, null
                                )
                        ),
                        new YoutubeApiResponse.YoutubeContentDetails("PT1M")
                )
        ));

        given(youtubeApiClient.fetchVideoInfo(videoId)).willReturn(apiResponse);

        // when
        VideoInfoResponse response = youtubeService.getVideo(url);

        // then
        assertThat(response.thumbnailUrl()).isEqualTo(standardUrl);
        assertThat(response.duration()).isEqualTo(60L);  // PT1M = 60
    }
}

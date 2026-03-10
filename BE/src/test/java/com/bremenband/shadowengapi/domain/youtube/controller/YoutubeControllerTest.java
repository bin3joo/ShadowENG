package com.bremenband.shadowengapi.domain.youtube.controller;

import com.bremenband.shadowengapi.domain.youtube.dto.res.VideoInfoResponse;
import com.bremenband.shadowengapi.domain.youtube.service.YoutubeService;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YoutubeController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class YoutubeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YoutubeService youtubeService;

    @Test
    @DisplayName("유효한 URL을 전달하면 영상 정보와 200을 반환한다")
    void getVideo_유효한URL_200() throws Exception {
        // given
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String videoId = "dQw4w9WgXcQ";

        VideoInfoResponse response = new VideoInfoResponse(
                videoId,
                "https://www.youtube.com/embed/" + videoId,
                "Rick Astley - Never Gonna Give You Up",
                "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg",
                212L,
                "Rick Astley"
        );

        given(youtubeService.getVideo(url)).willReturn(response);

        // when & then
        mockMvc.perform(get("/youtube")
                        .param("url", url)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.videoId").value(videoId))
                .andExpect(jsonPath("$.data.embedUrl").value("https://www.youtube.com/embed/" + videoId))
                .andExpect(jsonPath("$.data.title").value("Rick Astley - Never Gonna Give You Up"))
                .andExpect(jsonPath("$.data.thumbnailUrl")
                        .value("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg"))
                .andExpect(jsonPath("$.data.duration").value(212))
                .andExpect(jsonPath("$.data.channelTitle").value("Rick Astley"));

        then(youtubeService).should(times(1)).getVideo(url);
    }

    @Test
    @DisplayName("잘못된 URL을 전달하면 400을 반환한다")
    void getVideo_잘못된URL_400() throws Exception {
        // given
        String invalidUrl = "https://www.naver.com/watch?v=abc";

        given(youtubeService.getVideo(invalidUrl))
                .willThrow(new CustomException(ErrorCode.INVALID_YOUTUBE_URL));

        // when & then
        mockMvc.perform(get("/youtube")
                        .param("url", invalidUrl)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_YOUTUBE_URL.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_YOUTUBE_URL.getMessage()));
    }

    @Test
    @DisplayName("YouTube에 없는 영상 ID면 404를 반환한다")
    void getVideo_존재하지않는영상_404() throws Exception {
        // given
        String url = "https://www.youtube.com/watch?v=notExistVid";

        given(youtubeService.getVideo(url))
                .willThrow(new CustomException(ErrorCode.VIDEO_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/youtube")
                        .param("url", url)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VIDEO_NOT_FOUND.getMessage()));
    }
}

package com.bremenband.shadowengapi.domain.bookmark.controller;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkUpdateResponse;
import com.bremenband.shadowengapi.domain.bookmark.service.BookmarkService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class BookmarkUpdateControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BookmarkService bookmarkService;

    @Test
    @DisplayName("isBookmarked=true 요청 시 북마크 설정 후 200을 반환한다")
    void updateBookmark_설정_성공_200() throws Exception {
        // given
        Long userId = 1L;
        Long sentenceId = 1234L;

        BookmarkUpdateResponse response = new BookmarkUpdateResponse(sentenceId, "I got it bad.", true);

        given(bookmarkService.updateBookmark(userId, sentenceId, true)).willReturn(response);

        // when & then
        mockMvc.perform(patch("/sentences/{sentenceId}", sentenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isBookmarked\": true}")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sentenceId").value(sentenceId))
                .andExpect(jsonPath("$.data.sentence").value("I got it bad."))
                .andExpect(jsonPath("$.data.isBookmarked").value(true));

        then(bookmarkService).should(times(1)).updateBookmark(userId, sentenceId, true);
    }

    @Test
    @DisplayName("isBookmarked=false 요청 시 북마크 해제 후 200을 반환한다")
    void updateBookmark_해제_성공_200() throws Exception {
        // given
        Long userId = 1L;
        Long sentenceId = 1234L;

        BookmarkUpdateResponse response = new BookmarkUpdateResponse(sentenceId, "I got it bad.", false);

        given(bookmarkService.updateBookmark(userId, sentenceId, false)).willReturn(response);

        // when & then
        mockMvc.perform(patch("/sentences/{sentenceId}", sentenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isBookmarked\": false}")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isBookmarked").value(false));

        then(bookmarkService).should(times(1)).updateBookmark(userId, sentenceId, false);
    }

    @Test
    @DisplayName("존재하지 않는 문장이면 404를 반환한다")
    void updateBookmark_문장없음_404() throws Exception {
        // given
        Long userId = 1L;
        Long sentenceId = 999L;

        given(bookmarkService.updateBookmark(userId, sentenceId, true))
                .willThrow(new CustomException(ErrorCode.SENTENCE_NOT_FOUND));

        // when & then
        mockMvc.perform(patch("/sentences/{sentenceId}", sentenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isBookmarked\": true}")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SENTENCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SENTENCE_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("isBookmarked 필드가 없으면 400을 반환한다")
    void updateBookmark_필드누락_400() throws Exception {
        // given
        Long userId = 1L;
        Long sentenceId = 1234L;

        // when & then
        mockMvc.perform(patch("/sentences/{sentenceId}", sentenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}

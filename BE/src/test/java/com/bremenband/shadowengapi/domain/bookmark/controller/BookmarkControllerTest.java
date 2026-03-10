package com.bremenband.shadowengapi.domain.bookmark.controller;

import com.bremenband.shadowengapi.domain.bookmark.dto.res.BookmarkListResponse;
import com.bremenband.shadowengapi.domain.bookmark.service.BookmarkService;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class BookmarkControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BookmarkService bookmarkService;

    @Test
    @DisplayName("북마크가 있으면 목록과 200을 반환한다")
    void getBookmarks_성공_200() throws Exception {
        // given
        Long userId = 1L;

        BookmarkListResponse response = new BookmarkListResponse(List.of(
                new BookmarkListResponse.BookmarkItem(1234L, "I got it bad.", 12345L),
                new BookmarkListResponse.BookmarkItem(1235L, "She's got it good.", 12346L)
        ));

        given(bookmarkService.getBookmarks(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/bookmarks")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bookmarks").isArray())
                .andExpect(jsonPath("$.data.bookmarks.length()").value(2))
                .andExpect(jsonPath("$.data.bookmarks[0].sentenceId").value(1234))
                .andExpect(jsonPath("$.data.bookmarks[0].sentence").value("I got it bad."))
                .andExpect(jsonPath("$.data.bookmarks[0].sessionId").value(12345))
                .andExpect(jsonPath("$.data.bookmarks[1].sentenceId").value(1235))
                .andExpect(jsonPath("$.data.bookmarks[1].sentence").value("She's got it good."))
                .andExpect(jsonPath("$.data.bookmarks[1].sessionId").value(12346));

        then(bookmarkService).should(times(1)).getBookmarks(userId);
    }

    @Test
    @DisplayName("북마크가 없으면 빈 배열과 200을 반환한다")
    void getBookmarks_북마크없음_빈배열_200() throws Exception {
        // given
        Long userId = 2L;

        BookmarkListResponse response = new BookmarkListResponse(List.of());

        given(bookmarkService.getBookmarks(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/bookmarks")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.bookmarks").isArray())
                .andExpect(jsonPath("$.data.bookmarks.length()").value(0));

        then(bookmarkService).should(times(1)).getBookmarks(userId);
    }
}

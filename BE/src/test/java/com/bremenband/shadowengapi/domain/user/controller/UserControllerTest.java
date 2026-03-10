package com.bremenband.shadowengapi.domain.user.controller;

import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.bremenband.shadowengapi.global.config.SecurityConfig;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("유효한 Access Token으로 요청하면 사용자 정보와 200을 반환한다")
    void getUserData_성공() throws Exception {
        // given
        Long userId = 1L;
        UserInfoResponse response = new UserInfoResponse(
                userId,
                "브레맨",
                "user@example.com",
                15,
                LocalDateTime.of(2025, 8, 1, 10, 0, 0)
        );

        given(userService.getUserInfo(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("브레맨"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.visitedCount").value(15))
                .andExpect(jsonPath("$.data.createdAt").value("2025-08-01T10:00:00"));

        then(userService).should(times(1)).getUserInfo(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 요청하면 404와 isSuccess=false를 반환한다")
    void getUserData_존재하지않는사용자_404() throws Exception {
        // given
        Long userId = 999L;

        given(userService.getUserInfo(userId))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/me")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of())
                        )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());

        then(userService).should(times(1)).getUserInfo(userId);
    }
}

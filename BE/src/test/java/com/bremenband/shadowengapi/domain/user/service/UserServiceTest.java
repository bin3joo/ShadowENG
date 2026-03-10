package com.bremenband.shadowengapi.domain.user.service;

import com.bremenband.shadowengapi.domain.user.dto.res.UserInfoResponse;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("존재하는 userId로 조회하면 사용자 정보를 반환한다")
    void getUserInfo_성공() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .email("user@example.com")
                .nickname("브레맨")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "visitedCount", 15);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2025, 8, 1, 10, 0, 0));

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserInfoResponse response = userService.getUserInfo(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.nickname()).isEqualTo("브레맨");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.visitedCount()).isEqualTo(15);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2025, 8, 1, 10, 0, 0));

        then(userRepository).should(times(1)).findById(userId);
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 USER_NOT_FOUND 예외를 던진다")
    void getUserInfo_존재하지않는사용자_예외() {
        // given
        Long userId = 999L;

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserInfo(userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(userRepository).should(times(1)).findById(userId);
    }
}

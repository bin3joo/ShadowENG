package com.bremenband.shadowengapi.domain.auth.service;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.jwt.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GuestAuthServiceTest {

    @InjectMocks private GuestAuthService guestAuthService;

    @Mock private UserRepository      userRepository;
    @Mock private UserService         userService;
    @Mock private JwtProvider         jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;

    private static final String DEVICE_ID    = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ACCESS_TOKEN  = "access.token";
    private static final String REFRESH_TOKEN = "refresh.token";

    @Test
    @DisplayName("신규 디바이스 ID로 로그인하면 게스트 User가 생성되고 토큰이 발급된다")
    void guestLogin_신규디바이스_게스트생성후토큰발급() {
        // given
        User savedUser = User.builder()
                .email(null)
                .nickname("게스트_550e84")
                .provider("GUEST")
                .providerId(DEVICE_ID)
                .build();
        ReflectionTestUtils.setField(savedUser, "id", 1L);

        given(userRepository.findByProviderAndProviderId("GUEST", DEVICE_ID))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtProvider.generateAccessToken(1L)).willReturn(ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(1L)).willReturn(REFRESH_TOKEN);

        // when
        TokenResponse response = guestAuthService.guestLogin(DEVICE_ID);

        // then
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("게스트_550e84");
        assertThat(captor.getValue().getProvider()).isEqualTo("GUEST");
        assertThat(captor.getValue().getProviderId()).isEqualTo(DEVICE_ID);
        assertThat(captor.getValue().getEmail()).isNull();

        then(refreshTokenService).should(times(1)).save(1L, REFRESH_TOKEN);
        then(userService).should(times(1)).incrementVisitedCount(1L);
    }

    @Test
    @DisplayName("기존 디바이스 ID로 로그인하면 기존 User를 재사용하고 새 User를 생성하지 않는다")
    void guestLogin_기존디바이스_기존유저재사용() {
        // given
        User existingUser = User.builder()
                .email(null)
                .nickname("게스트_550e84")
                .provider("GUEST")
                .providerId(DEVICE_ID)
                .build();
        ReflectionTestUtils.setField(existingUser, "id", 42L);

        given(userRepository.findByProviderAndProviderId("GUEST", DEVICE_ID))
                .willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(42L)).willReturn(ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(42L)).willReturn(REFRESH_TOKEN);

        // when
        TokenResponse response = guestAuthService.guestLogin(DEVICE_ID);

        // then
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);

        then(userRepository).should(never()).save(any());
        then(refreshTokenService).should(times(1)).save(42L, REFRESH_TOKEN);
        then(userService).should(times(1)).incrementVisitedCount(42L);
    }

    @Test
    @DisplayName("게스트 로그인 시 닉네임은 'guest_' + deviceId 앞 6자리로 생성된다")
    void guestLogin_닉네임형식_검증() {
        // given
        String deviceId = "abcdef12-0000-0000-0000-000000000000";
        User savedUser = User.builder()
                .email(null)
                .nickname("게스트_abcdef")
                .provider("GUEST")
                .providerId(deviceId)
                .build();
        ReflectionTestUtils.setField(savedUser, "id", 1L);

        given(userRepository.findByProviderAndProviderId("GUEST", deviceId))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtProvider.generateAccessToken(1L)).willReturn(ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(1L)).willReturn(REFRESH_TOKEN);

        // when
        guestAuthService.guestLogin(deviceId);

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("게스트_abcdef");
    }
}

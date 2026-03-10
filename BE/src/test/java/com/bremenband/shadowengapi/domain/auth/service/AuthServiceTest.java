package com.bremenband.shadowengapi.domain.auth.service;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.jwt.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;

    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 토큰을 발급하고 Redis를 갱신한다")
    void refresh_성공() {
        // given
        String oldRefresh  = "old.refresh.token";
        String newAccess   = "new.access.token";
        String newRefresh  = "new.refresh.token";
        Long userId = 1L;

        given(jwtProvider.isValid(oldRefresh)).willReturn(true);
        given(jwtProvider.isRefreshToken(oldRefresh)).willReturn(true);
        given(jwtProvider.getUserId(oldRefresh)).willReturn(userId);
        given(refreshTokenService.validate(userId, oldRefresh)).willReturn(true);
        given(jwtProvider.generateAccessToken(userId)).willReturn(newAccess);
        given(jwtProvider.generateRefreshToken(userId)).willReturn(newRefresh);

        // when
        TokenResponse response = authService.refresh(oldRefresh);

        // then
        assertThat(response.accessToken()).isEqualTo(newAccess);
        assertThat(response.refreshToken()).isEqualTo(newRefresh);
        then(refreshTokenService).should(times(1)).save(userId, newRefresh);
    }

    @Test
    @DisplayName("유효하지 않은 JWT이면 INVALID_TOKEN 예외를 던진다")
    void refresh_유효하지않은JWT_예외() {
        // given
        String token = "invalid.token";
        given(jwtProvider.isValid(token)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("access 타입 토큰을 refresh 엔드포인트에 사용하면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void refresh_액세스토큰사용_예외() {
        // given
        String accessToken = "access.type.token";
        given(jwtProvider.isValid(accessToken)).willReturn(true);
        given(jwtProvider.isRefreshToken(accessToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Redis에 저장된 토큰과 다르면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void refresh_Redis불일치_예외() {
        // given
        String refreshToken = "refresh.token";
        Long userId = 1L;

        given(jwtProvider.isValid(refreshToken)).willReturn(true);
        given(jwtProvider.isRefreshToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(userId);
        given(refreshTokenService.validate(userId, refreshToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그아웃하면 Redis에서 리프레시 토큰이 삭제된다")
    void logout_성공() {
        // given
        Long userId = 1L;

        // when
        authService.logout(userId);

        // then
        then(refreshTokenService).should(times(1)).delete(userId);
    }
}

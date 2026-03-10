package com.bremenband.shadowengapi.domain.auth.service;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public TokenResponse refresh(String refreshToken) {
        // 1. 유효한 JWT인지 검증
        if (!jwtProvider.isValid(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // 2. refresh 타입 토큰인지 검증
        if (!jwtProvider.isRefreshToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. userId 추출 후 Redis 저장값과 일치 여부 확인
        Long userId = jwtProvider.getUserId(refreshToken);
        if (!refreshTokenService.validate(userId, refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 4. 새 토큰 발급 (refresh token rotation)
        String newAccessToken  = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        // 5. 새 리프레시 토큰으로 교체 저장
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }
}

package com.bremenband.shadowengapi.domain.auth.facade;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DevAuthFacade {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public TokenResponse devLogin(Long userId) {
        String accessToken  = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken);
        userService.incrementVisitedCount(userId);
        return new TokenResponse(accessToken, refreshToken);
    }
}

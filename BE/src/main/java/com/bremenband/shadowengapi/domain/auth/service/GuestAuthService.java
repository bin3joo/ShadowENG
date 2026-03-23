package com.bremenband.shadowengapi.domain.auth.service;

import com.bremenband.shadowengapi.domain.auth.dto.res.TokenResponse;
import com.bremenband.shadowengapi.domain.user.entity.User;
import com.bremenband.shadowengapi.domain.user.repository.UserRepository;
import com.bremenband.shadowengapi.domain.user.service.UserService;
import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import com.bremenband.shadowengapi.global.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestAuthService {

    private static final String GUEST_PROVIDER = "GUEST";

    private final UserRepository      userRepository;
    private final UserService         userService;
    private final JwtProvider         jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenResponse guestLogin(String deviceId) {
        User user = userRepository.findByProviderAndProviderId(GUEST_PROVIDER, deviceId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(null)
                                .nickname("게스트_" + deviceId.substring(0, 6))
                                .provider(GUEST_PROVIDER)
                                .providerId(deviceId)
                                .build()
                ));

        String accessToken  = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);
        userService.incrementVisitedCount(user.getId());

        return new TokenResponse(accessToken, refreshToken);
    }
}

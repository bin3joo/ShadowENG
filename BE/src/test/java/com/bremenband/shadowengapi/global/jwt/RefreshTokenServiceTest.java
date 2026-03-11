package com.bremenband.shadowengapi.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    @Mock
    private ValueOperations<String, String> valueOps;

    private static final long REFRESH_EXPIRY_MS    = 604_800_000L;   // 7일
    private static final long MAX_SESSION_EXPIRY_MS = 2_592_000_000L; // 30일

    private static final Long   USER_ID   = 1L;
    private static final String TOKEN_KEY = "refresh_token:1";
    private static final String ABS_KEY   = "refresh_token_abs_expiry:1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiryMs",  REFRESH_EXPIRY_MS);
        ReflectionTestUtils.setField(refreshTokenService, "maxSessionExpiryMs", MAX_SESSION_EXPIRY_MS);
        // lenient: delete(), validate_절대세션만료 등 opsForValue()를 호출하지 않는 테스트에서 불필요한 스텁 경고 방지
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── save() : 신규 세션 ────────────────────────────────────────────────────

    @Test
    @DisplayName("신규 세션: 절대 만료 키가 없으면 절대 만료 키(30일)와 토큰 키(7일)를 모두 신규 설정한다")
    void save_신규세션_절대만료키없음_두키모두설정() {
        // given: 절대 만료 키 없음(-2 = key does not exist)
        given(redisTemplate.getExpire(ABS_KEY, TimeUnit.MILLISECONDS)).willReturn(-2L);

        // when
        refreshTokenService.save(USER_ID, "refresh.token");

        // then
        then(valueOps).should().set(ABS_KEY, "1", Duration.ofMillis(MAX_SESSION_EXPIRY_MS));
        then(valueOps).should().set(TOKEN_KEY, "refresh.token", Duration.ofMillis(REFRESH_EXPIRY_MS));
    }

    // ── save() : RTR 회전 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("RTR 회전: 절대 만료 여유 충분(20일 남음)이면 슬라이딩 TTL(7일)로 토큰을 재설정한다")
    void save_RTR회전_절대만료여유충분_토큰TTL은슬라이딩7일() {
        // given: 절대 만료까지 20일 남음 (> 7일)
        long twentyDaysMs = 20L * 24 * 60 * 60 * 1000;
        given(redisTemplate.getExpire(ABS_KEY, TimeUnit.MILLISECONDS)).willReturn(twentyDaysMs);

        // when
        refreshTokenService.save(USER_ID, "new.token");

        // then: 절대 만료 키는 갱신하지 않음
        then(valueOps).should(never()).set(eq(ABS_KEY), any(), any(Duration.class));
        // 토큰 TTL = min(7일, 20일) = 7일
        then(valueOps).should().set(TOKEN_KEY, "new.token", Duration.ofMillis(REFRESH_EXPIRY_MS));
    }

    @Test
    @DisplayName("RTR 회전: 절대 만료 임박(2일 남음)이면 토큰 TTL을 남은 절대 만료 시간(2일)으로 제한한다")
    void save_RTR회전_절대만료임박_토큰TTL은남은절대만료시간() {
        // given: 절대 만료까지 2일 남음 (< 7일)
        long twoDaysMs = 2L * 24 * 60 * 60 * 1000;
        given(redisTemplate.getExpire(ABS_KEY, TimeUnit.MILLISECONDS)).willReturn(twoDaysMs);

        // when
        refreshTokenService.save(USER_ID, "new.token");

        // then: 토큰 TTL = min(7일, 2일) = 2일 → 슬라이딩이 절대 만료를 초과하지 않음
        then(valueOps).should().set(TOKEN_KEY, "new.token", Duration.ofMillis(twoDaysMs));
    }

    // ── validate() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("절대 세션 유효 + 토큰 일치 → true")
    void validate_정상_true() {
        // given
        String token = "valid.refresh.token";
        given(redisTemplate.hasKey(ABS_KEY)).willReturn(true);
        given(valueOps.get(TOKEN_KEY)).willReturn(token);

        // when & then
        assertThat(refreshTokenService.validate(USER_ID, token)).isTrue();
    }

    @Test
    @DisplayName("절대 세션 만료(절대 만료 키 소멸) → false, 토큰 조회 생략")
    void validate_절대세션만료_false() {
        // given: 절대 만료 키가 TTL 초과로 소멸
        given(redisTemplate.hasKey(ABS_KEY)).willReturn(false);

        // when & then
        assertThat(refreshTokenService.validate(USER_ID, "any.token")).isFalse();
        // 절대 세션이 만료되면 토큰 조회 자체를 하지 않아야 함
        then(valueOps).should(never()).get(any());
    }

    @Test
    @DisplayName("절대 세션 유효하지만 토큰 키 없음(로그아웃 상태) → false")
    void validate_토큰키없음_false() {
        // given: 절대 만료 키는 있지만 토큰 키가 없음(logout으로 삭제됨)
        given(redisTemplate.hasKey(ABS_KEY)).willReturn(true);
        given(valueOps.get(TOKEN_KEY)).willReturn(null);

        // when & then
        assertThat(refreshTokenService.validate(USER_ID, "any.token")).isFalse();
    }

    @Test
    @DisplayName("절대 세션 유효하지만 토큰 불일치(탈취 후 회전된 상태) → false")
    void validate_토큰불일치_false() {
        // given: 정상 사용자가 이미 토큰을 회전하여 Redis에는 새 토큰이 저장됨
        given(redisTemplate.hasKey(ABS_KEY)).willReturn(true);
        given(valueOps.get(TOKEN_KEY)).willReturn("rotated.new.token");

        // when & then: 공격자가 이전 토큰으로 요청
        assertThat(refreshTokenService.validate(USER_ID, "old.stolen.token")).isFalse();
    }

    // ── delete() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: 토큰 키와 절대 만료 키를 모두 삭제하여 세션을 완전히 무효화한다")
    void delete_토큰키와절대만료키_모두삭제() {
        // when
        refreshTokenService.delete(USER_ID);

        // then
        then(redisTemplate).should().delete(TOKEN_KEY);
        then(redisTemplate).should().delete(ABS_KEY);
    }
}

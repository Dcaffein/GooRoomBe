package com.example.DunbarHorizon.account.application;

import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.application.port.out.AuthTokenProvider;
import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.application.service.LoginService;
import com.example.DunbarHorizon.account.domain.*;
import com.example.DunbarHorizon.account.domain.repository.AuthRepository;
import com.example.DunbarHorizon.account.domain.repository.RefreshTokenRepository;
import com.example.DunbarHorizon.account.domain.exception.RefreshTokenNotFoundException;
import com.example.DunbarHorizon.account.domain.exception.TokenTheftDetectedException;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import com.example.DunbarHorizon.global.event.DeviceTokenDeregisteredEvent;
import com.example.DunbarHorizon.global.security.AuthPrincipal;
import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @InjectMocks private LoginService loginService;

    @Mock private UserRepository userRepository;
    @Mock private AuthRepository authRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthTokenProvider authTokenProvider;
    @Mock private PasswordHasher passwordHasher;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("로그인 시 비밀번호를 검증하고 토큰을 발급한다")
    void login_Success() {
        // given
        User user = User.builder().email("test@test.com").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        Auth auth = spy(Auth.createLocalAuth(1L, "encoded-pw"));
        ReflectionTestUtils.setField(auth, "verified", true);

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(authRepository.findByUserIdAndProvider(1L, AuthProvider.LOCAL)).willReturn(Optional.of(auth));
        given(passwordHasher.matches(anyString(), anyString())).willReturn(true);
        given(authTokenProvider.createAccessToken(any())).willReturn("at");

        // when
        AuthTokenResult result = loginService.login("test@test.com", "password");

        // then
        assertThat(result.accessToken()).isEqualTo("at");
        verify(refreshTokenRepository).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("재발급 시 refresh_token 쿠키가 없으면 토큰 파싱을 시도하지 않고 RefreshTokenNotFoundException이 발생한다")
    void reissue_쿠키가_없으면_파싱_전에_예외(String missingToken) {
        // given - 파싱까지 넘기면 InvalidTokenException으로 뭉뚱그려져
        //         "만료"와 "애초에 로그인 상태가 아님"이 구분되지 않는다

        // when & then
        assertThatThrownBy(() -> loginService.reissue(missingToken))
                .isInstanceOf(RefreshTokenNotFoundException.class);

        verify(authTokenProvider, never()).validateToken(any());
        verify(refreshTokenRepository, never()).findByTokenValue(any());
    }

    @Test
    @DisplayName("재발급 시 토큰은 유효하나 DB에 없으면 해당 사용자의 모든 토큰을 삭제하고 TokenTheftDetectedException이 발생한다")
    void reissue_DB에_없으면_전체_삭제_후_예외() {
        // given
        String stolenToken = "valid-but-unknown-rt";
        given(authTokenProvider.validateToken(stolenToken)).willReturn(new AuthPrincipal(1L, "ROLE_USER"));
        given(refreshTokenRepository.findByTokenValue(stolenToken)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginService.reissue(stolenToken))
                .isInstanceOf(TokenTheftDetectedException.class);

        verify(refreshTokenRepository).deleteAllByUserId(1L);
    }

    @Test
    @DisplayName("재발급 시 토큰 검증에서 발생한 예외는 그대로 전파된다")
    void reissue_검증_실패는_그대로_전파() {
        // given - LoginService는 토큰 유효성 판정에 관여하지 않는다.
        //         변환은 JwtTokenProvider가 이미 수행했으므로 여기서는 통과시키기만 한다.
        String expiredToken = "expired-rt";
        given(authTokenProvider.validateToken(expiredToken)).willThrow(new ExpiredTokenException());

        // when & then
        assertThatThrownBy(() -> loginService.reissue(expiredToken))
                .isInstanceOf(ExpiredTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenValue(any());
    }

    @Test
    @DisplayName("재발급 성공 시 새 토큰으로 회전시키고 결과를 반환한다")
    void reissue_Success() {
        // given
        String oldToken = "old-rt";
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_USER");
        RefreshToken stored = RefreshToken.builder()
                .userId(1L)
                .tokenValue(oldToken)
                .expiryDate(LocalDateTime.now(ZoneOffset.UTC).plusDays(7))
                .build();

        given(authTokenProvider.validateToken(oldToken)).willReturn(principal);
        given(refreshTokenRepository.findByTokenValue(oldToken)).willReturn(Optional.of(stored));
        given(authTokenProvider.createAccessToken(principal)).willReturn("new-at");
        given(authTokenProvider.createRefreshToken(principal)).willReturn("new-rt");
        given(authTokenProvider.getExpirationTime("new-rt"))
                .willReturn(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));

        // when
        AuthTokenResult result = loginService.reissue(oldToken);

        // then
        assertThat(result.accessToken()).isEqualTo("new-at");
        assertThat(result.refreshToken()).isEqualTo("new-rt");
        assertThat(stored.getTokenValue()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("로그아웃 시 refreshToken과 fcmToken이 모두 있으면 토큰 삭제 후 이벤트를 발행한다")
    void logout_refreshToken과_fcmToken이_있으면_삭제하고_이벤트_발행() {
        // when
        loginService.logout("refresh-token-value", "fcm-token-value");

        // then
        verify(refreshTokenRepository).deleteByTokenValue("refresh-token-value");

        ArgumentCaptor<DeviceTokenDeregisteredEvent> captor =
                ArgumentCaptor.forClass(DeviceTokenDeregisteredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().fcmToken()).isEqualTo("fcm-token-value");
    }

    @Test
    @DisplayName("로그아웃 시 fcmToken이 없으면 refreshToken만 삭제하고 이벤트를 발행하지 않는다")
    void logout_fcmToken이_없으면_refreshToken만_삭제() {
        // when
        loginService.logout("refresh-token-value", null);

        // then
        verify(refreshTokenRepository).deleteByTokenValue("refresh-token-value");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("로그아웃 시 refreshToken이 없어도 fcmToken이 있으면 이벤트를 발행한다")
    void logout_refreshToken이_없어도_fcmToken이_있으면_이벤트_발행() {
        // when
        loginService.logout(null, "fcm-token-value");

        // then
        verify(refreshTokenRepository, never()).deleteByTokenValue(any());
        verify(eventPublisher).publishEvent(any(DeviceTokenDeregisteredEvent.class));
    }
}

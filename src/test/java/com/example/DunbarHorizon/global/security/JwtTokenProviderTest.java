package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import com.example.DunbarHorizon.global.security.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "thisIsAVeryLongAndSecureSecretKeyForJWTAuthUsingHS512Algorithm1234567890abcdefghijklmn";
    // 서명 위조 검증용. HS512는 디코딩 후 64바이트 이상이어야 하므로 secret과 길이를 맞춘다.
    private final String otherSecret = "ZZZZIsAVeryLongAndSecureSecretKeyForJWTAuthUsingHS512Algorithm1234567890abcdefghijklmn";

    @BeforeEach
    void setUp() {
        // 3600초 (1시간) 설정
        jwtTokenProvider = new JwtTokenProvider(secret, 3600, 7200);
    }

    @Test
    @DisplayName("UserPrincipal 정보를 바탕으로 유효한 토큰을 생성한다")
    void createToken_Success() {
        // given
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_USER");

        // when
        String token = jwtTokenProvider.createAccessToken(principal);

        // then
        assertThat(token).isNotBlank();
        AuthPrincipal validated = jwtTokenProvider.validateToken(token);
        assertThat(validated.id()).isEqualTo(1L);
        assertThat(validated.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("만료된 토큰을 검증하면 ExpiredTokenException으로 변환되어 발생한다")
    void validateToken_Expired() {
        // given - 만료 시간이 0인 프로바이더로 이미 만료된 토큰을 만든다
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, 0, 0);
        String token = expiredProvider.createAccessToken(new AuthPrincipal(1L, "ROLE_USER"));

        // when & then
        assertThatThrownBy(() -> expiredProvider.validateToken(token))
                .isInstanceOf(ExpiredTokenException.class)
                .hasMessage("만료된 토큰입니다.");
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰을 검증하면 InvalidTokenException으로 변환되어 발생한다")
    void validateToken_ForgedSignature() {
        // given - 서명 실패는 io.jsonwebtoken.security.SignatureException이며 JwtException 하위다
        JwtTokenProvider forgedProvider = new JwtTokenProvider(otherSecret, 3600, 7200);
        String forgedToken = forgedProvider.createAccessToken(new AuthPrincipal(1L, "ROLE_USER"));

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(forgedToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not-a-jwt", "aaa.bbb.ccc"})
    @DisplayName("null·공백·형식이 잘못된 토큰을 검증하면 InvalidTokenException으로 변환되어 발생한다")
    void validateToken_Malformed(String token) {
        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("검증 실패 시 io.jsonwebtoken 예외가 클래스 밖으로 새어나가지 않는다")
    void validateToken_DoesNotLeakJjwtException() {
        // given
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, 0, 0);
        String token = expiredProvider.createAccessToken(new AuthPrincipal(1L, "ROLE_USER"));

        // when & then
        assertThatThrownBy(() -> expiredProvider.validateToken(token))
                .satisfies(thrown -> assertThat(
                        thrown.getClass().getName().startsWith("io.jsonwebtoken")).isFalse());
    }
}

package com.example.DunbarHorizon.account.adapter.in.web.OAuth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    private static final String FRONTEND_BASE_URL = "http://localhost:3000";

    @InjectMocks
    private OAuth2AuthenticationFailureHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "frontendBaseUrl", FRONTEND_BASE_URL);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("동의 화면에서 취소하면 에러 파라미터 없이 로그인 화면으로 돌려보낸다")
    void onAuthenticationFailure_UserCancelled_RedirectsWithoutErrorParam() throws IOException {
        // given
        AuthenticationException exception = oauth2Exception("access_denied");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then — 취소는 오류가 아니므로 프론트가 분기할 값을 주지 않는다
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/login");
    }

    @Test
    @DisplayName("공급자가 이메일을 검증하지 않은 경우 email_not_verified를 붙여 리다이렉트한다")
    void onAuthenticationFailure_EmailNotVerified_RedirectsWithCode() throws IOException {
        // given
        AuthenticationException exception = oauth2Exception("email_not_verified");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getRedirectedUrl())
                .isEqualTo(FRONTEND_BASE_URL + "/login?error=email_not_verified");
    }

    @Test
    @DisplayName("정의되지 않은 OAuth2 에러 코드는 failed 하나로 뭉갠다")
    void onAuthenticationFailure_UnknownOAuth2Code_RedirectsWithFailed() throws IOException {
        // given
        AuthenticationException exception = oauth2Exception("invalid_token_response");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then — 공급자 코드가 그대로 새어 나가지 않는다
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/login?error=failed");
        assertThat(response.getRedirectedUrl()).doesNotContain("invalid_token_response");
    }

    @Test
    @DisplayName("OAuth2AuthenticationException이 아닌 인증 예외도 failed로 처리한다")
    void onAuthenticationFailure_NonOAuth2Exception_RedirectsWithFailed() throws IOException {
        // given
        AuthenticationException exception = new BadCredentialsException("자격 증명이 올바르지 않습니다");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/login?error=failed");
    }

    @Test
    @DisplayName("예외 메시지와 설명이 리다이렉트 URL에 노출되지 않는다")
    void onAuthenticationFailure_DoesNotLeakExceptionDetail() throws IOException {
        // given
        OAuth2Error error = new OAuth2Error(
                "invalid_request", "민감한내부설명", "https://example.com/internal");
        AuthenticationException exception =
                new OAuth2AuthenticationException(error, "예외메시지원문");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getRedirectedUrl())
                .doesNotContain("민감한내부설명")
                .doesNotContain("예외메시지원문")
                .doesNotContain("example.com")
                .doesNotContain("OAuth2AuthenticationException");
    }

    @Test
    @DisplayName("리다이렉트 대상은 설정된 프론트엔드 주소를 따른다")
    void onAuthenticationFailure_UsesConfiguredFrontendBaseUrl() throws IOException {
        // given
        ReflectionTestUtils.setField(handler, "frontendBaseUrl", "https://www.dunbarhorizon.com");

        // when
        handler.onAuthenticationFailure(request, response, oauth2Exception("email_not_verified"));

        // then
        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://www.dunbarhorizon.com/login?error=email_not_verified");
    }

    private AuthenticationException oauth2Exception(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode), errorCode);
    }
}

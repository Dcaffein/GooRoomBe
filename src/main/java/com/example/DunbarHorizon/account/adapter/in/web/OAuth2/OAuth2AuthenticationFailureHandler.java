package com.example.DunbarHorizon.account.adapter.in.web.OAuth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth 로그인 실패 시 프론트엔드로 돌려보낸다.
 *
 * <p>이 시점의 요청은 API 호출이 아니라 공급자에서 넘어온 브라우저 이동이다. 응답을 기다리는
 * 클라이언트 코드가 없으므로 JSON을 내려주면 브라우저가 그대로 화면에 그린다. 복귀 수단은
 * 리다이렉트뿐이고, 실패 사유는 쿼리 파라미터로 넘긴다.
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** 프론트 라우팅을 백엔드가 알고 있는 구조라 한 곳에 모아둔다. */
    private static final String LOGIN_PATH = "/login";
    private static final String ERROR_PARAM = "error";

    /** 공급자가 이메일 소유를 검증하지 않은 계정. {@code CustomOAuth2UserService}가 던진다. */
    private static final String ERROR_EMAIL_NOT_VERIFIED = "email_not_verified";
    private static final String ERROR_DEFAULT = "failed";

    /** 사용자가 동의 화면에서 취소하면 공급자가 이 코드로 돌려보낸다. */
    private static final String PROVIDER_CODE_ACCESS_DENIED = "access_denied";

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    /**
     * {@code super.onAuthenticationFailure}를 호출하지 않는다. 부모 구현은 실패 예외를 세션에
     * 담는 경로를 타는데 이 앱은 {@code SessionCreationPolicy.STATELESS}다.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (isUserCancelled(exception)) {
            log.info("[oauth2] 사용자가 동의를 취소했다.");
            getRedirectStrategy().sendRedirect(request, response, loginUrl());
            return;
        }

        String errorCode = resolveErrorCode(exception);
        log.warn("[oauth2] 인증 실패. error={}", errorCode, exception);
        getRedirectStrategy().sendRedirect(request, response, loginUrlWith(errorCode));
    }

    private boolean isUserCancelled(AuthenticationException exception) {
        return PROVIDER_CODE_ACCESS_DENIED.equals(providerErrorCode(exception));
    }

    /**
     * 공급자·Spring이 주는 코드는 무엇이든 올 수 있다. 아는 값만 골라 정해진 문자열로 바꾸고
     * 나머지는 전부 하나로 뭉갠다. 원본 코드와 예외는 로그에만 남긴다 — 그대로 URL에 실으면
     * 백엔드 내부 표현이 노출되고, 프론트가 렌더링할 경우 반사형 XSS 표면이 된다.
     */
    private String resolveErrorCode(AuthenticationException exception) {
        return ERROR_EMAIL_NOT_VERIFIED.equals(providerErrorCode(exception))
                ? ERROR_EMAIL_NOT_VERIFIED
                : ERROR_DEFAULT;
    }

    private String providerErrorCode(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauth2Exception)) {
            return null;
        }
        return oauth2Exception.getError() != null ? oauth2Exception.getError().getErrorCode() : null;
    }

    private String loginUrl() {
        return frontendBaseUrl + LOGIN_PATH;
    }

    private String loginUrlWith(String errorCode) {
        return loginUrl() + "?" + ERROR_PARAM + "=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
    }
}

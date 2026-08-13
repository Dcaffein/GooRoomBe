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

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String LOGIN_PATH = "/login";
    private static final String ERROR_PARAM = "error";

    private static final String ERROR_EMAIL_NOT_VERIFIED = "email_not_verified";
    private static final String ERROR_DEFAULT = "failed";

    private static final String PROVIDER_CODE_ACCESS_DENIED = "access_denied";

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

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

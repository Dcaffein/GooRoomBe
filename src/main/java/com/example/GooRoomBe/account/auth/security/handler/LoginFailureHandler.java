package com.example.GooRoomBe.account.auth.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String errorMessage;
        String errorCode;

        if (exception instanceof LockedException) {
            errorMessage = exception.getMessage();
            errorCode = "UNVERIFIED";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = "이메일 또는 비밀번호가 올바르지 않습니다.";
            errorCode = "BAD_CREDENTIALS";
        } else {
            errorMessage = "로그인에 실패했습니다. 관리자에게 문의하세요.";
            errorCode = "AUTH_FAILED";
        }

        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("error", "Authentication Failed");
        errorDetails.put("code", errorCode);
        errorDetails.put("message", errorMessage);

        String jsonResponse = objectMapper.writeValueAsString(errorDetails);
        response.getWriter().write(jsonResponse);
    }
}
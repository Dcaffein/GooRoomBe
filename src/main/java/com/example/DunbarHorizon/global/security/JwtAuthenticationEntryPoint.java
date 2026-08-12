package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.exception.BusinessException;
import com.example.DunbarHorizon.global.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Exception exception = (Exception) request.getAttribute("exception");

        log.warn("인증 실패 - 예외 타입: {}, 메시지: {}",
                exception != null ? exception.getClass().getSimpleName() : "null",
                authException.getMessage());

        // 토큰이 아예 없어 예외조차 발생하지 않은 경우의 기본값.
        String errorName = "UnAuthorizedException";
        String message = "인증되지 않은 사용자입니다.";

        // JwtTokenProvider가 jjwt 예외를 BusinessException으로 변환해두므로 타입 나열이 필요 없다.
        // GlobalExceptionHandler와 동일하게 예외에서 error/message를 파생시킨다.
        // 두 출구가 같은 방식으로 값을 뽑아내므로 응답 어휘가 어긋날 수 없다.
        if (exception instanceof BusinessException businessException) {
            errorName = businessException.getClass().getSimpleName();
            message = businessException.getMessage();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(errorName)
                .message(message)
                .build();

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

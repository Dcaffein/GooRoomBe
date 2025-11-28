package com.example.GooRoomBe.account.auth.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        final String exceptionCode = (String) request.getAttribute("exceptionCode");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        response.setContentType("application/json;charset=UTF-8");

        String errorCode = (exceptionCode != null) ? exceptionCode : "AUTHENTICATION_REQUIRED";

        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("errorCode", errorCode);
        responseMap.put("message", errorCode);

        response.getWriter().write(objectMapper.writeValueAsString(responseMap));
    }
}
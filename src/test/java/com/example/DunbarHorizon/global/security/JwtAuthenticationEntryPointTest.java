package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import com.example.DunbarHorizon.global.security.exception.InvalidTokenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
    }

    private static AuthenticationException authException() {
        return new AuthenticationException("인증이 필요합니다.") {
        };
    }

    private JsonNode commenceAndParse(MockHttpServletRequest request,
                                      MockHttpServletResponse response) throws IOException {
        entryPoint.commence(request, response, authException());
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("만료 예외가 저장되어 있으면 예외에서 파생된 error와 message로 401을 응답한다")
    void commence_ExpiredToken() throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("exception", new ExpiredTokenException());

        // when
        JsonNode body = commenceAndParse(request, response);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.get("error").asText()).isEqualTo("ExpiredTokenException");
        assertThat(body.get("message").asText()).isEqualTo("만료된 토큰입니다.");
    }

    @Test
    @DisplayName("위조 토큰 예외가 저장되어 있으면 InvalidTokenException으로 401을 응답한다")
    void commence_InvalidToken() throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("exception", new InvalidTokenException());

        // when
        JsonNode body = commenceAndParse(request, response);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.get("error").asText()).isEqualTo("InvalidTokenException");
        assertThat(body.get("message").asText()).isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    @DisplayName("토큰이 아예 없어 저장된 예외가 없으면 기본 인증 실패 응답을 반환한다")
    void commence_NoException() throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        JsonNode body = commenceAndParse(request, response);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.get("error").asText()).isEqualTo("UnAuthorizedException");
        assertThat(body.get("message").asText()).isEqualTo("인증되지 않은 사용자입니다.");
    }

    @Test
    @DisplayName("인증과 무관한 예외가 저장되어 있으면 내부 정보를 노출하지 않고 기본 응답을 반환한다")
    void commence_NonBusinessException() throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("exception", new IllegalStateException("내부 구현 세부사항"));

        // when
        JsonNode body = commenceAndParse(request, response);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.get("error").asText()).isEqualTo("UnAuthorizedException");
        assertThat(body.get("message").asText()).doesNotContain("내부 구현 세부사항");
    }
}

package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.exception.BusinessException;
import com.example.DunbarHorizon.global.exception.ErrorResponse;
import com.example.DunbarHorizon.global.exception.GlobalExceptionHandler;
import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import com.example.DunbarHorizon.global.security.exception.InvalidTokenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 실패 응답의 출구는 둘이며 구조적으로 합쳐질 수 없다.
 *
 * <ul>
 *   <li>{@link JwtAuthenticationEntryPoint} — Security 필터 체인. access token 실패를 담당한다.</li>
 *   <li>{@link GlobalExceptionHandler} — Spring MVC. 재발급 요청(refresh token) 실패를 담당한다.</li>
 * </ul>
 *
 * <p>{@code @RestControllerAdvice}는 {@code DispatcherServlet} 내부 예외만 보고, EntryPoint는
 * 필터 체인의 장치이므로 MVC 예외를 볼 수 없다. 따라서 두 출구는 서로 도달할 수 없다.
 *
 * <p>과거 EntryPoint가 응답 문자열을 하드코딩한 탓에 같은 "만료"가 경로에 따라
 * {@code TokenExpiredException} / {@code ExpiredTokenException}으로 갈렸다. 프론트가 두 경로를
 * 같은 조건으로 분기할 수 없게 되는 회귀이므로, 두 출구가 동일한 예외에 대해 동일한 응답을
 * 만들어내는지 여기서 못박는다.
 */
class AuthErrorResponseConsistencyTest {

    private JwtAuthenticationEntryPoint entryPoint;
    private GlobalExceptionHandler globalExceptionHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    static Stream<Arguments> sharedTokenExceptions() {
        return Stream.of(
                Arguments.of("만료", new ExpiredTokenException()),
                Arguments.of("위조/형식 오류", new InvalidTokenException())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedTokenExceptions")
    @DisplayName("동일한 토큰 예외에 대해 필터 경로와 재발급 경로가 같은 error/message/status를 응답한다")
    void bothExitsProduceIdenticalResponse(String label, BusinessException exception) throws IOException {
        // given - 필터 경로: 필터가 request 속성에 저장한 예외를 EntryPoint가 읽는다
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("exception", exception);

        // when - 필터 경로
        entryPoint.commence(request, response, new AuthenticationException("인증이 필요합니다.") {
        });
        JsonNode filterPathBody = objectMapper.readTree(response.getContentAsString());

        // when - 재발급 경로: 같은 예외가 컨트롤러 밖으로 나와 전역 핸들러에 도달한다
        ResponseEntity<ErrorResponse> handled = globalExceptionHandler.handleBusinessException(exception);
        ErrorResponse reissuePathBody = handled.getBody();

        // then
        assertThat(reissuePathBody).isNotNull();
        assertThat(filterPathBody.get("error").asText())
                .as("두 출구의 error 어휘가 일치해야 한다")
                .isEqualTo(reissuePathBody.getError());
        assertThat(filterPathBody.get("message").asText())
                .as("두 출구의 message가 일치해야 한다")
                .isEqualTo(reissuePathBody.getMessage());
        assertThat(response.getStatus())
                .as("두 출구의 HTTP 상태가 일치해야 한다")
                .isEqualTo(handled.getStatusCode().value());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedTokenExceptions")
    @DisplayName("두 출구 모두 응답의 error를 예외 클래스명에서 파생시킨다")
    void errorNameIsDerivedFromExceptionClass(String label, BusinessException exception) throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute("exception", exception);

        // when
        entryPoint.commence(request, response, new AuthenticationException("인증이 필요합니다.") {
        });
        JsonNode filterPathBody = objectMapper.readTree(response.getContentAsString());

        // then - 하드코딩된 문자열이 아니라 클래스명에서 파생되어야 드리프트가 발생하지 않는다
        assertThat(filterPathBody.get("error").asText())
                .isEqualTo(exception.getClass().getSimpleName());
    }
}

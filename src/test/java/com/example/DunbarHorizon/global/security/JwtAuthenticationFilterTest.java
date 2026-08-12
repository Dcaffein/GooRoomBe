package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock
    private JwtTokenProvider jwtProvider;
    @Mock
    private AuthCookieManager authCookieManager;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 쿠키 토큰이 있으면 시큐리티 컨텍스트에 인증 정보가 설정된다")
    void doFilterInternal_Success() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String token = "valid-token";
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_USER");

        given(authCookieManager.extractAccessToken(request)).willReturn(token);
        given(jwtProvider.validateToken(token)).willReturn(principal);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthPrincipal) auth.getPrincipal()).id()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰 검증에 실패해도 필터 체인은 계속 진행되고 예외가 request 속성에 저장된다")
    void doFilterInternal_ValidationFailed_ContinuesChain() throws ServletException, IOException {
        // given - 체인을 끊으면 permitAll 엔드포인트가 막힌다. 특히 만료된 access token을 들고 오는
        //         토큰 재발급 요청(PATCH /api/auth/tokens)이 컨트롤러에 도달하지 못하게 된다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String token = "expired-token";

        given(authCookieManager.extractAccessToken(request)).willReturn(token);
        willThrow(new ExpiredTokenException()).given(jwtProvider).validateToken(token);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("exception")).isInstanceOf(ExpiredTokenException.class);
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("인증과 무관한 예기치 못한 예외가 발생해도 필터 체인은 계속 진행된다")
    void doFilterInternal_UnexpectedException_ContinuesChain() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String token = "some-token";

        given(authCookieManager.extractAccessToken(request)).willReturn(token);
        willThrow(new IllegalStateException("예상 밖 결함")).given(jwtProvider).validateToken(token);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(request.getAttribute("exception")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("토큰 쿠키가 없으면 검증을 시도하지 않고 체인만 진행한다")
    void doFilterInternal_NoToken_ContinuesChain() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(authCookieManager.extractAccessToken(request)).willReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("exception")).isNull();
    }
}

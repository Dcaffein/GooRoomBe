package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtProvider;
    private final AuthCookieManager authCookieManager;

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = authCookieManager.extractAccessToken(request);

        if (token != null && !token.isBlank()) {
            try {
                AuthPrincipal principal = jwtProvider.validateToken(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                // 만료·위조 등 예상된 인증 실패. 장애가 아니므로 debug로 남기고,
                // 응답 생성은 JwtAuthenticationEntryPoint가 단일 지점에서 담당한다.
                log.debug("JWT 인증 실패: {}", e.getMessage());
                request.setAttribute("exception", e);
            } catch (Exception e) {
                // 인증과 무관한 예상 밖 결함. debug로 묻히면 안 된다.
                log.error("JWT 검증 중 예기치 못한 오류", e);
                request.setAttribute("exception", e);
            }
        }
        // 검증 실패 시에도 체인을 계속 진행해야 한다. 여기서 응답을 쓰거나 체인을 끊으면
        // permitAll 엔드포인트가 막힌다. 특히 만료된 access token을 들고 오는 토큰 재발급
        // 요청(PATCH /api/auth/tokens)은 정상 시나리오이므로 컨트롤러까지 도달해야 한다.
        filterChain.doFilter(request, response);
    }
}

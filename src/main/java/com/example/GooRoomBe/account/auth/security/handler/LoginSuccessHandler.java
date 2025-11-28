package com.example.GooRoomBe.account.auth.security.handler;

import com.example.GooRoomBe.account.auth.security.cookie.AuthCookieFactory;
import com.example.GooRoomBe.account.auth.domain.token.RefreshToken;
import com.example.GooRoomBe.account.auth.security.provider.JwtTokenProvider;
import com.example.GooRoomBe.account.auth.repository.RefreshTokenRepository;
import com.example.GooRoomBe.account.auth.security.principal.LocalUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthCookieFactory authCookieFactory;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {

        LocalUserDetails userDetails = (LocalUserDetails) authentication.getPrincipal();
        String userId = String.valueOf(userDetails.getUser().getId());

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshTokenValue = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenRepository.findByUser_Id(userId)
                .ifPresentOrElse(
                        refreshToken -> {
                            refreshToken.updateTokenValue(refreshTokenValue);
                            refreshTokenRepository.save(refreshToken);
                        },
                        () -> {
                            RefreshToken newRefreshToken = new RefreshToken(userDetails.getUser(), refreshTokenValue);
                            refreshTokenRepository.save(newRefreshToken);
                        }
                );

        ResponseCookie accessTokenCookie = authCookieFactory.createAccessTokenCookie(accessToken);
        ResponseCookie refreshTokenCookie = authCookieFactory.createRefreshTokenCookie(refreshTokenValue);

        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
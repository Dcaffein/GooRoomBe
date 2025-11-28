package com.example.GooRoomBe.account.auth.security.handler;

import com.example.GooRoomBe.account.auth.security.cookie.AuthCookieFactory;
import com.example.GooRoomBe.account.auth.domain.token.RefreshToken;
import com.example.GooRoomBe.account.auth.security.provider.JwtTokenProvider;
import com.example.GooRoomBe.account.auth.repository.RefreshTokenRepository;
import com.example.GooRoomBe.account.auth.security.principal.CustomOAuth2User;
import com.example.GooRoomBe.account.user.domain.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieFactory authCookieFactory;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.frontend.base-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        User user = oAuth2User.getUser();
        String userId = user.getId();

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshTokenValue = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenRepository.findByUser_Id(userId)
                .ifPresentOrElse(
                        refreshToken -> {
                            refreshToken.updateTokenValue(refreshTokenValue);
                            refreshTokenRepository.save(refreshToken);
                        },
                        () -> {
                            RefreshToken newRefreshToken = new RefreshToken(user, refreshTokenValue);
                            refreshTokenRepository.save(newRefreshToken);
                        }
                );

        ResponseCookie accessTokenCookie = authCookieFactory.createAccessTokenCookie(accessToken);
        ResponseCookie refreshTokenCookie = authCookieFactory.createRefreshTokenCookie(refreshTokenValue);

        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        clearAuthenticationAttributes(request);
        response.sendRedirect(frontendUrl + "/");
    }
}
package com.example.GooRoomBe.account.auth.security.cookie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

    @Value("${jwt.access-expiration-seconds}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration-seconds}")
    private long refreshExpiration;

    public ResponseCookie createAccessTokenCookie(String token) {
        return createCookie("accessToken", token, accessExpiration);
    }

    public ResponseCookie createRefreshTokenCookie(String token) {
        return createCookie("refreshToken", token, refreshExpiration);
    }

    /**
     * 로그아웃용 만료 쿠키 생성
     */
    public ResponseCookie createExpiredCookie(String cookieName) {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // 즉시 만료
                .sameSite("None")
                .build();
    }

    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite("None")
                .build();
    }
}
package com.example.DunbarHorizon.account.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 재발급 요청에 refresh_token 쿠키가 실려오지 않은 경우.
 *
 * <p>토큰이 만료된 경우와는 구분된다. 만료는 {@code ExpiredTokenException}이 담당한다.
 */
public class RefreshTokenNotFoundException extends AccountException {
    public RefreshTokenNotFoundException() {
        super("로그인 정보가 없습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
    }
}
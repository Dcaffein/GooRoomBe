package com.example.GooRoomBe.account.auth.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenNotFoundException extends AuthException {
    public RefreshTokenNotFoundException() {
        super("로그인 정보가 없거나 만료되었습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
    }
}
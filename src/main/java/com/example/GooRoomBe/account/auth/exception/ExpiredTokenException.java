package com.example.GooRoomBe.account.auth.exception;

import org.springframework.http.HttpStatus;

public class ExpiredTokenException extends AuthException {
    public ExpiredTokenException() {
        super("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED);
    }
}
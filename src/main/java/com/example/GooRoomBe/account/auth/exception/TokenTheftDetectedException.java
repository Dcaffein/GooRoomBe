package com.example.GooRoomBe.account.auth.exception;

import org.springframework.http.HttpStatus;

public class TokenTheftDetectedException extends AuthException {
    public TokenTheftDetectedException() {
        super("보안 위협이 감지되었습니다(토큰 재사용). 안전을 위해 다시 로그인해주세요.", HttpStatus.FORBIDDEN);
    }
}
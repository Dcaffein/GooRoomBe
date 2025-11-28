package com.example.GooRoomBe.account.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailSendFailureException extends AuthException {
    public EmailSendFailureException() {
        super("이메일 전송 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
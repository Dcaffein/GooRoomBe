package com.example.GooRoomBe.account.auth.exception;

import com.example.GooRoomBe.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public abstract class AuthException extends BusinessException {
    protected AuthException(String message, HttpStatus httpStatus) {
        super(message, httpStatus);
    }
}

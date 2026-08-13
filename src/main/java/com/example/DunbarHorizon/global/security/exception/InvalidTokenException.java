package com.example.DunbarHorizon.global.security.exception;

import com.example.DunbarHorizon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends BusinessException {
    public InvalidTokenException() {
        super("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
    }
}

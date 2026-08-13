package com.example.DunbarHorizon.global.security.exception;

import com.example.DunbarHorizon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ExpiredTokenException extends BusinessException {
    public ExpiredTokenException() {
        super("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED);
    }
}

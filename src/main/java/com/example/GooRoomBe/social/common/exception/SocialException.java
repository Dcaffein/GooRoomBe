package com.example.GooRoomBe.social.common.exception;

import com.example.GooRoomBe.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public abstract class SocialException extends BusinessException {
    protected SocialException(String message, HttpStatus httpStatus) {
        super(message, httpStatus);
    }
}

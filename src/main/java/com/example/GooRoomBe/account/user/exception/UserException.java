package com.example.GooRoomBe.account.user.exception;

import com.example.GooRoomBe.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserException extends BusinessException {
    public UserException(String message, HttpStatus httpStatus) {
        super(message,httpStatus);
    }
}

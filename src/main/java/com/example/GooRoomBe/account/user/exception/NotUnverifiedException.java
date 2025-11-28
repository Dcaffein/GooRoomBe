package com.example.GooRoomBe.account.user.exception;

import org.springframework.http.HttpStatus;

public class NotUnverifiedException extends UserException{
    public NotUnverifiedException(String userId) {
        super("not unverified user : " + userId, HttpStatus.CONFLICT);
    }
}

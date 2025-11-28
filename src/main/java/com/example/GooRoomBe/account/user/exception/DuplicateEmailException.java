package com.example.GooRoomBe.account.user.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends UserException {
    public DuplicateEmailException(String email) {
        super(email, HttpStatus.CONFLICT);
    }
}

package com.example.DunbarHorizon.flag.domain.memorial.exception;

import org.springframework.http.HttpStatus;

public class FlagMemorialInvalidContentException extends FlagMemorialException {
    public FlagMemorialInvalidContentException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

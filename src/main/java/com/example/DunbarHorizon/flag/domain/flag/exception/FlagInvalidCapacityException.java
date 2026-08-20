package com.example.DunbarHorizon.flag.domain.flag.exception;

import org.springframework.http.HttpStatus;

public class FlagInvalidCapacityException extends FlagException {
    public FlagInvalidCapacityException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

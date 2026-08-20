package com.example.DunbarHorizon.flag.domain.comment.exception;

import org.springframework.http.HttpStatus;

public class FlagCommentInvalidContentException extends FlagCommentException {
    public FlagCommentInvalidContentException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

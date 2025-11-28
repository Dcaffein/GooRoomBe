package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class LabelAuthorizationException extends SocialException {
    public LabelAuthorizationException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
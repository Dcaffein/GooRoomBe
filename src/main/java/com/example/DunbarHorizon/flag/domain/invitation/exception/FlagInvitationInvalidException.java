package com.example.DunbarHorizon.flag.domain.invitation.exception;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagException;
import org.springframework.http.HttpStatus;

public class FlagInvitationInvalidException extends FlagException {
    public FlagInvitationInvalidException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

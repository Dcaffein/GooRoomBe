package com.example.DunbarHorizon.flag.application.dto;

import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationInvalidException;

import java.util.Locale;

public enum FlagInvitationDirection {
    RECEIVED,
    SENT;

    public static FlagInvitationDirection from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new FlagInvitationInvalidException("direction은 received 또는 sent만 사용할 수 있습니다.");
        }
    }
}

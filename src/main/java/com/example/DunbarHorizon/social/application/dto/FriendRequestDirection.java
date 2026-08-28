package com.example.DunbarHorizon.social.application.dto;

import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestInvalidException;

import java.util.Locale;

public enum FriendRequestDirection {
    RECEIVED,
    SENT;

    public static FriendRequestDirection from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new FriendRequestInvalidException(
                    "direction은 received 또는 sent만 허용됩니다."
            );
        }
    }
}

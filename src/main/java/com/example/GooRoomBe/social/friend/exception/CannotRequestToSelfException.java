package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class CannotRequestToSelfException extends SocialException {
    public CannotRequestToSelfException(String userId) {
        super(String.format("자기 자신(ID: %s)에게는 친구 요청을 보낼 수 없습니다.", userId), HttpStatus.BAD_REQUEST);
    }
}
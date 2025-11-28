package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendRequestNotAcceptedException extends SocialException {
    public FriendRequestNotAcceptedException(String requestId) {
        super(String.format("친구 요청(ID: %s)은 수락(ACCEPTED) 상태가 아닙니다.", requestId), HttpStatus.BAD_REQUEST);
    }
}

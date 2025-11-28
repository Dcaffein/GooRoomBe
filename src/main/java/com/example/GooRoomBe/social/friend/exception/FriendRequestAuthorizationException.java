package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendRequestAuthorizationException extends SocialException {
    public FriendRequestAuthorizationException(String requestId, String userId) {
        super(String.format("User(ID: %s)는 친구 요청(ID: %s)을 처리할 권한이 없습니다.", userId, requestId), HttpStatus.FORBIDDEN);
    }
}
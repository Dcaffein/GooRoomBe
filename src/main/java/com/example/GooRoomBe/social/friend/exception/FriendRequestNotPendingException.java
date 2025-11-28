package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendRequestNotPendingException extends SocialException {
    public FriendRequestNotPendingException(String requestId) {
        super(String.format("친구 요청(ID: %s)은 대기(PENDING) 상태가 아닙니다.", requestId), HttpStatus.BAD_REQUEST);
    }
}
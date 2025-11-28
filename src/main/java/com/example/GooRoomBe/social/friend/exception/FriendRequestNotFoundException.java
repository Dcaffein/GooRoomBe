package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendRequestNotFoundException extends SocialException {
    // ID를 알 때
    public FriendRequestNotFoundException(String requestId) {
        super(String.format("해당 친구 요청(ID: %s)을 찾을 수 없습니다.", requestId), HttpStatus.NOT_FOUND);
    }

    // ID를 모를 때
//    public FriendRequestNotFoundException() {
//        super("해당 친구 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
//    }
}
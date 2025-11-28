package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendshipAuthorizationException extends SocialException {
    public FriendshipAuthorizationException(String userId) {
        super(String.format("User(%s)는 해당 친구 관계를 관리할 권한이 없습니다.", userId), HttpStatus.FORBIDDEN);
    }

    public FriendshipAuthorizationException() {
        super("해당 친구 관계를 관리할 권한이 없습니다.", HttpStatus.FORBIDDEN);
    }
}
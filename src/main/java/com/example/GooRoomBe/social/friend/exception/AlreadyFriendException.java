package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class AlreadyFriendException extends SocialException {
    public AlreadyFriendException(String user1Id, String user2Id) {
        super(String.format("User(%s)와 User(%s)는 이미 친구 관계입니다.", user1Id, user2Id), HttpStatus.CONFLICT);
    }
}

package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class FriendshipNotFoundException extends SocialException {
    public FriendshipNotFoundException(String user1Id, String user2Id) {
        super(String.format("User(%s)와 User(%s) 사이의 친구 관계를 찾을 수 없습니다.", user1Id, user2Id), HttpStatus.NOT_FOUND);
    }

    public FriendshipNotFoundException(String friendShipId) {
        super(String.format("해당 친구 관계(%s)를 찾을 수 없습니다.", friendShipId), HttpStatus.NOT_FOUND);
    }

}
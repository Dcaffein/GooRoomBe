package com.example.GooRoomBe.social.friend.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class DuplicateFriendRequestException extends SocialException {
    public DuplicateFriendRequestException(String requesterId, String receiverId) {
        super(String.format("이미 친구 요청이 존재합니다. (Sender: %s, Receiver: %s)", requesterId, receiverId), HttpStatus.CONFLICT);
    }
}
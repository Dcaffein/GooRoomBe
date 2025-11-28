package com.example.GooRoomBe.social.common.exception;

import org.springframework.http.HttpStatus;

public class SocialUserNotFoundException extends SocialException {
    public SocialUserNotFoundException(String userId) {
      super(String.format("User(%s)에 대한 참조를 찾지 못했습니다", userId), HttpStatus.NOT_FOUND);
    }
}

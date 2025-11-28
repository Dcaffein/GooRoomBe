package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class LabelNameDuplicateException extends SocialException {
  public LabelNameDuplicateException(String ownerId, String labelName) {
    super(String.format("User(%s)는 이미 '%s'라는 이름의 라벨을 가지고 있습니다.", ownerId, labelName), HttpStatus.CONFLICT);
  }
}
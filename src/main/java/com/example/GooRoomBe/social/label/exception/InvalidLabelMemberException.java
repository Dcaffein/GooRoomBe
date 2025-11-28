package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidLabelMemberException extends SocialException {
  public InvalidLabelMemberException(List<String> invalidMemberIds) {
    super(String.format("다음 사용자들은 친구 관계가 아니므로 멤버로 추가할 수 없습니다: %s", invalidMemberIds), HttpStatus.BAD_REQUEST);
  }
}
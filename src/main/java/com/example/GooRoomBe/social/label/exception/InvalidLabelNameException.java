package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class InvalidLabelNameException extends SocialException {
    public InvalidLabelNameException() {
        super("라벨 이름은 공백일 수 없습니다.", HttpStatus.BAD_REQUEST);
    }
}
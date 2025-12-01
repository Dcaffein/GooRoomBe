package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class LabelNotFoundException extends SocialException {
    public LabelNotFoundException(String labelId) {
        super("해당 id를 가진 label을 찾을 수 없습니다 : " + labelId, HttpStatus.NOT_FOUND);
    }
}

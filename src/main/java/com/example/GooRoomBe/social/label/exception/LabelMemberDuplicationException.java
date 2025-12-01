package com.example.GooRoomBe.social.label.exception;

import com.example.GooRoomBe.social.common.exception.SocialException;
import org.springframework.http.HttpStatus;

public class LabelMemberDuplicationException extends SocialException {
    public LabelMemberDuplicationException( String newMemberId) {
        super("이미 라벨 멤버인 사용자는 추가할 수 없습니다 : " + newMemberId, HttpStatus.CONFLICT);
    }
}

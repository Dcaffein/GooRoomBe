package com.example.DunbarHorizon.account.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 가입 인증 토큰이 만료되었거나, 존재하지 않거나, 이미 사용되었다.
 *
 * <p>세 경우를 구분하지 않는다. 구분해봐야 사용자가 할 일은 "이메일부터 다시 입력"으로 같고,
 * 토큰 존재 여부를 알려주면 대입 시도의 피드백이 된다.
 */
public class InvalidVerificationTokenException extends AccountException {
    public InvalidVerificationTokenException() {
        super("유효하지 않거나 만료된 인증 링크입니다.", HttpStatus.GONE);
    }
}

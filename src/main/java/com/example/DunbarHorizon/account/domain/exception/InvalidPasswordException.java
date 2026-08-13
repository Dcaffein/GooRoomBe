package com.example.DunbarHorizon.account.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 비밀번호가 정책을 어겼다. 웹 계층의 {@code @Pattern}이 먼저 거르므로 정상 경로에서는
 * 도달하지 않고, 어댑터를 거치지 않는 진입로가 생겼을 때만 나타난다.
 */
public class InvalidPasswordException extends AccountException {
    public InvalidPasswordException() {
        super(com.example.DunbarHorizon.account.domain.policy.PasswordPolicy.MESSAGE, HttpStatus.BAD_REQUEST);
    }
}

package com.example.DunbarHorizon.account.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 비밀번호가 규칙을 어겼다. 웹 계층의 {@code @Pattern}이 먼저 거르므로 정상 경로에서는
 * 도달하지 않고, 어댑터를 거치지 않는 진입로가 생겼을 때만 나타난다.
 *
 * <p>메시지를 {@code Auth}에서 받는다. 규칙과 문구가 같은 자리에 있어야 갈리지 않는다.
 */
public class InvalidPasswordException extends AccountException {
    public InvalidPasswordException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

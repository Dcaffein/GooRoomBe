package com.example.DunbarHorizon.account.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 로그인 실패. 미가입 이메일·LOCAL 자격증명 없음·비밀번호 불일치를 구분하지 않는다.
 *
 * <p>메시지를 생성자에서 고정하고 인자를 받지 않는다. 호출부가 문구를 주입할 수 있으면
 * 경로별로 응답이 갈려 계정 열거가 다시 열리기 때문이다. 실패 사유는 서버 로그에만 남긴다.
 *
 * <p>{@code GlobalExceptionHandler}가 {@code getMessage()}를 그대로 응답 본문에 싣는다는 점에
 * 유의할 것 — 이 메시지에 넣는 값은 전부 외부로 나간다.
 */
public class InvalidCredentialsException extends AccountException {
    public InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
    }
}

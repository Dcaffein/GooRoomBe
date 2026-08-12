package com.example.DunbarHorizon.global.security.exception;

import com.example.DunbarHorizon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * JWT의 서명이 위조되었거나, 형식이 잘못되었거나, 토큰 문자열이 비어 있는 경우.
 *
 * <p>{@code JwtTokenProvider.validateToken}에서만 발생하며, access token(필터 경로)과
 * refresh token(재발급 경로)이 공유한다. 두 경로 모두 이 클래스의 이름과 메시지를 그대로
 * 응답의 {@code error} / {@code message}로 사용하므로, 이름 변경은 프론트 API 계약 변경이다.
 */
public class InvalidTokenException extends BusinessException {
    public InvalidTokenException() {
        super("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
    }
}

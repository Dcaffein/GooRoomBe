package com.example.DunbarHorizon.account.domain.policy;

import com.example.DunbarHorizon.account.domain.exception.InvalidPasswordException;

/**
 * 비밀번호 규칙의 단일 진실 공급원.
 *
 * <p>이전에는 규칙이 {@code SignupRequestDto}의 {@code @Pattern} 하나뿐이었다. 비밀번호를
 * 받는 두 번째 지점(재설정)이 생기면 정규식이 복제되고, 두 곳이 갈리는 순간 약한 쪽으로만
 * 통과하는 구멍이 생긴다.
 *
 * <p>웹 계층의 {@code @Pattern}을 없애지는 않는다 — 형식 오류를 즉시 400으로 돌려주는 것은
 * 어댑터의 일이다. 다만 그 애너테이션이 여기의 상수를 참조하므로 규칙은 한 벌만 존재한다.
 */
public final class PasswordPolicy {

    /** 영문·숫자·특수문자(!@#$%^&amp;*) 포함 8~20자. */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,20}$";
    public static final String MESSAGE = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자로 입력해주세요.";

    private PasswordPolicy() {
    }

    /**
     * 규칙을 어기면 예외를 던진다. 자격증명이 만들어지는 지점에서 호출되므로,
     * 어느 진입 경로로 들어오든 규칙을 우회할 수 없다.
     */
    public static void validate(String rawPassword) {
        if (rawPassword == null || !rawPassword.matches(REGEX)) {
            throw new InvalidPasswordException();
        }
    }
}

package com.example.DunbarHorizon.account.domain;

/**
 * 도메인이 비밀번호를 다루기 위해 필요한 최소 능력.
 *
 * <p>구현은 어댑터에 있지만 인터페이스를 도메인에 두어, {@code Auth}가 해시를 밖으로
 * 내보내지 않고도 자격증명을 만들고 대조할 수 있게 한다. 이게 없으면 서비스가
 * {@code auth.getPassword()}로 해시를 꺼내 비교해야 한다.
 *
 * <p>출력 포트 {@code PasswordHasher}가 이 인터페이스를 상속하므로 주입된 빈을 그대로 넘기면 된다.
 */
public interface PasswordCipher {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}

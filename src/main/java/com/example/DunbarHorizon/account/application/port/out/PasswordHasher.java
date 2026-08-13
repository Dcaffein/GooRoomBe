package com.example.DunbarHorizon.account.application.port.out;

import com.example.DunbarHorizon.account.domain.PasswordCipher;

/**
 * 비밀번호 해싱 출력 포트. 도메인이 요구하는 능력({@link PasswordCipher})을 그대로 만족하므로,
 * 주입받은 빈을 도메인 팩토리와 대조 메서드에 그대로 넘길 수 있다.
 */
public interface PasswordHasher extends PasswordCipher {
}

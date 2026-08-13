package com.example.DunbarHorizon.account.application.port.out;

import com.example.DunbarHorizon.account.domain.HashedPassword;

/**
 * 해싱 결과를 {@link HashedPassword}로 돌려준다. 이 인터페이스의 구현만 그 타입을 만들 수
 * 있으므로, 저장되는 값이 해싱을 거쳤다는 사실이 타입으로 보장된다.
 */
public interface PasswordHasher {

    HashedPassword hash(String rawPassword);

    boolean matches(String rawPassword, HashedPassword hashedPassword);
}

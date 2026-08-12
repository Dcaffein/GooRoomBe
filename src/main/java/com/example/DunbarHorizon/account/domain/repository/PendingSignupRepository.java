package com.example.DunbarHorizon.account.domain.repository;

import java.util.Optional;

/**
 * 이메일 소유 증명이 끝나기 전의 가입 접수를 보관한다.
 *
 * <p>보관하는 것은 이메일뿐이며 자격증명은 담지 않는다. 비밀번호는 링크 클릭으로 소유가
 * 증명된 뒤에 입력받으므로, 이 저장소가 노출되어도 자격증명이 새지 않는다.
 *
 * <p>이메일 기준 dedup을 하지 않는다. 같은 이메일로 여러 번 접수하면 토큰이 각각 생성된다.
 * 기존 토큰을 무효화하면 공격자가 피해자 이메일로 반복 접수해 피해자의 링크를 계속
 * 죽이는 서비스 거부가 성립하기 때문이다.
 */
public interface PendingSignupRepository {

    void save(String token, String email);

    /**
     * 토큰을 소비하지 않고 이메일만 조회한다. 폼을 그리기 전 유효성 확인용.
     */
    Optional<String> findEmailByToken(String token);

    /**
     * 토큰을 원자적으로 소비한다. 조회와 삭제가 한 연산이므로 동시 요청이 같은 토큰으로
     * 두 번 계정을 만들 수 없다.
     */
    Optional<String> consumeEmailByToken(String token);
}

package com.example.DunbarHorizon.account.domain;

import com.example.DunbarHorizon.account.domain.policy.PasswordCipher;
import com.example.DunbarHorizon.account.domain.policy.PasswordPolicy;
import com.example.DunbarHorizon.global.common.BaseTimeAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "auths", indexes = {
        @Index(name = "idx_auth_user_provider", columnList = "user_id, provider")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auth extends BaseTimeAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    private String password;

    private String providerId;

    @Builder
    private Auth(Long userId, AuthProvider provider, String password, String providerId) {
        this.userId = userId;
        this.provider = provider;
        this.password = password;
        this.providerId = providerId;
    }

    /**
     * Auth 행의 존재가 곧 이메일 소유 증명이다. 로컬은 인증 메일이, OAuth는 공급자가 증명하며
     * 증명 전에는 행이 만들어지지 않는다. 그래서 {@code verified} 플래그가 존재하지 않고,
     * 기존 행의 비밀번호를 덮어쓰는 경로도 두지 않는다.
     */
    public static Auth createLocalAuth(Long userId, String rawPassword, PasswordCipher cipher) {
        PasswordPolicy.validate(rawPassword);
        return Auth.builder()
                .userId(userId)
                .provider(AuthProvider.LOCAL)
                .password(cipher.encode(rawPassword))
                .build();
    }

    /**
     * 해시를 밖으로 내보내지 않고 여기서 대조한다. 호출자가 {@code getPassword()}로 해시를
     * 꺼내면 그 값이 서비스·로그·응답으로 새어 나갈 경로가 그만큼 늘어난다.
     */
    public boolean matches(String rawPassword, PasswordCipher cipher) {
        return password != null && cipher.matches(rawPassword, password);
    }

    public static Auth createOAuth(Long userId, AuthProvider provider, String providerId) {
        return Auth.builder()
                .userId(userId)
                .provider(provider)
                .providerId(providerId)
                .build();
    }
}
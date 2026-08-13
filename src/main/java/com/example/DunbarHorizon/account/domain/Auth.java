package com.example.DunbarHorizon.account.domain;

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

    public static final String PASSWORD_REGEX =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,20}$";
    public static final String PASSWORD_MESSAGE =
            "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자로 입력해주세요.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Getter(AccessLevel.NONE)
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
    public static Auth createLocalAuth(Long userId, HashedPassword password) {
        return Auth.builder()
                .userId(userId)
                .provider(AuthProvider.LOCAL)
                .password(password.value())
                .build();
    }

    public static Auth createOAuth(Long userId, AuthProvider provider, String providerId) {
        return Auth.builder()
                .userId(userId)
                .provider(provider)
                .providerId(providerId)
                .build();
    }

    public HashedPassword hashedPassword() {
        return password == null ? null : new HashedPassword(password);
    }
}

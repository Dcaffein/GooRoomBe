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

    /**
     * 영문·숫자·특수문자(!@#$%^&amp;*) 포함 8~20자.
     *
     * <p>규칙의 정의는 여기 한 곳이고 강제는 웹 계층의 {@code @Pattern}이 한다. 비밀번호가
     * 들어오는 경로는 전부 DTO를 지나므로 — OAuth 계정은 비밀번호가 없다 — 도메인에서
     * 다시 검증할 이유가 없다. 비밀번호를 받는 엔드포인트가 늘어나도 이 상수를 참조하면
     * 규칙은 한 벌로 유지된다.
     */
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

    /** 컬럼은 문자열이지만 밖으로는 {@link HashedPassword}로만 나간다. */
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
     *
     * <p>인자가 {@link HashedPassword}인 이유는 평문을 넘길 수 없게 하기 위해서다.
     * {@code String}이면 인코딩을 빠뜨려도 컴파일되고, 평문이 저장된 채 로그인까지 정상 동작한다.
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

    /**
     * OAuth 자격증명에는 비밀번호가 없으므로 {@code null}일 수 있다.
     * 원시 문자열 게터를 막아두고 이 메서드만 노출한다.
     */
    public HashedPassword hashedPassword() {
        return password == null ? null : new HashedPassword(password);
    }
}

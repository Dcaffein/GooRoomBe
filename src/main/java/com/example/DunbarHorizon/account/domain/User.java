package com.example.DunbarHorizon.account.domain;

import com.example.DunbarHorizon.global.event.user.UserDeactivatedEvent;
import com.example.DunbarHorizon.global.event.user.UserProfileUpdatedEvent;
import com.example.DunbarHorizon.account.domain.event.UserDeletedEvent;
import java.time.LocalDateTime;
import java.util.Locale;
import com.example.DunbarHorizon.global.common.BaseTimeAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeAggregateRoot {

    public static final int NICKNAME_MIN_LENGTH = 1;
    public static final int NICKNAME_MAX_LENGTH = 20;
    public static final String NICKNAME_LENGTH_MESSAGE = "닉네임은 1자 이상 20자 이하로 입력해주세요.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 2048)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Builder
    public User(String email, String nickname, String profileImage, UserRole role, UserStatus status) {
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role != null ? role : UserRole.USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    /**
     * 계정 생성의 유일한 경로. 로컬·OAuth 모두 이메일 소유가 증명된 뒤에만 호출되므로
     * 생성 시점부터 {@code ACTIVE}다. 증명되지 않은 계정이라는 상태는 존재하지 않는다.
     *
     * <p>{@code UserActivatedEvent}는 여기서 등록하지 않는다. {@code @GeneratedValue}라
     * {@code save()} 전에는 {@code id}가 null이기 때문이며, 발행은 저장 직후 서비스가 맡는다.
     */
    public static User createActive(String email, String nickname) {
        return User.builder()
                .email(normalizeEmail(email))
                .nickname(normalizeNickname(nickname))
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void updateProfile(String nickname, String profileImage) {
        String normalized = normalizeNickname(nickname);
        this.nickname = normalized;
        this.profileImage = profileImage;
        this.registerEvent(new UserProfileUpdatedEvent(this.id, normalized, profileImage, LocalDateTime.now()));
    }

    public void deactivate() {
        if (this.status == UserStatus.ACTIVE) {
            this.status = UserStatus.DORMANT;
            this.registerEvent(new UserDeactivatedEvent(this.id));
        }
    }

    public void deleteAccount() {
        if (this.status != UserStatus.DELETED) {
            this.status = UserStatus.DELETED;
            this.registerEvent(new UserDeletedEvent(this.id));
        }
    }

    /**
     * 증명된 이메일이 곧 이 계정의 신원이므로 동일성 기준을 코드가 정해야 한다. 이 메서드가
     * 없던 동안 {@code Kim@x.com}과 {@code kim@x.com}이 같은 계정을 가리킨 것은 MySQL 기본
     * 콜레이션이 대소문자를 구분하지 않았기 때문이며, 그 의존은 어디에도 적혀 있지 않았다.
     *
     * <p>{@code UserRepositoryAdapter}가 조회 전에도 호출하므로 {@code public}이다. 쓰기와
     * 읽기가 같은 기준을 쓰지 않으면 정규화가 오히려 계정을 못 찾게 만든다.
     *
     * <p>{@code Locale.ROOT}를 명시하는 이유는 터키어 로케일에서 {@code I}가 점 없는
     * {@code ı}로 내려가기 때문이다. 서버 로케일에 따라 신원이 갈리면 안 된다.
     *
     * <p>gmail의 {@code .} 무시나 {@code +} 별칭 같은 공급자별 규칙은 적용하지 않는다.
     * 공급자마다 다르고, 틀리면 서로 다른 사람의 계정이 하나로 합쳐진다.
     */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 길이를 넘으면 거부하지 않고 잘라낸다. 로컬 가입은 사용자가 고칠 수 있어 웹 계층에서
     * 되돌려주면 되지만, OAuth는 공급자가 준 이름이라 사용자가 고칠 수 없다. 거기서 거부하면
     * 이름이 긴 사람은 로그인할 방법이 없어진다.
     *
     * <p>계정을 만드는 모든 경로가 {@code createActive}를 지나므로 여기 한 곳이면 된다.
     */
    private static String normalizeNickname(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() <= NICKNAME_MAX_LENGTH ? trimmed : trimmed.substring(0, NICKNAME_MAX_LENGTH);
    }
}

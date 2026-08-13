package com.example.DunbarHorizon.account.domain;

import com.example.DunbarHorizon.global.event.user.UserDeactivatedEvent;
import com.example.DunbarHorizon.global.event.user.UserProfileUpdatedEvent;
import com.example.DunbarHorizon.account.domain.event.UserDeletedEvent;
import com.example.DunbarHorizon.account.domain.policy.EmailPolicy;
import com.example.DunbarHorizon.account.domain.policy.NicknamePolicy;
import java.time.LocalDateTime;
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
                .email(EmailPolicy.normalize(email))
                .nickname(NicknamePolicy.normalize(nickname))
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void updateProfile(String nickname, String profileImage) {
        String normalized = NicknamePolicy.normalize(nickname);
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
}
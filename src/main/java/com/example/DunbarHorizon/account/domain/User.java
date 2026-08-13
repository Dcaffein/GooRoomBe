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

    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNickname(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() <= NICKNAME_MAX_LENGTH ? trimmed : trimmed.substring(0, NICKNAME_MAX_LENGTH);
    }
}

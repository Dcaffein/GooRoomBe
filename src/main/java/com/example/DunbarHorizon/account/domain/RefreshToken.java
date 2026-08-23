package com.example.DunbarHorizon.account.domain;

import com.example.DunbarHorizon.global.common.BaseTimeAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseTimeAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenValue;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Builder
    public RefreshToken(Long userId, String tokenValue, LocalDateTime expiryDate) {
        this.userId = userId;
        this.tokenValue = tokenValue;
        this.expiryDate =  expiryDate;
    }

    public void rotateTokenValue(String newRefreshToken, LocalDateTime newExpiryDate) {
        this.tokenValue = newRefreshToken;
        this.expiryDate = newExpiryDate;
    }
}
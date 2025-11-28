package com.example.GooRoomBe.account.auth.domain.token;

import com.example.GooRoomBe.account.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.LocalDateTime;

import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Node
public class VerificationToken {
    @Id @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String tokenValue;

    private static final int EXPIRATION_MINUTES = 60;

    private LocalDateTime expireDate;

    @Relationship(type = "VERIFIES", direction = Relationship.Direction.OUTGOING)
    private User user;

    public VerificationToken(User user) {
        this.tokenValue = UUID.randomUUID().toString();
        this.user = user;
        this.expireDate = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);
    }

    public void validateExpiration() {
        if (this.expireDate.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("만료된 토큰입니다.");
        }
    }
}
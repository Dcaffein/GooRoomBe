package com.example.GooRoomBe.account.user.domain;

import com.example.GooRoomBe.account.user.exception.NotUnverifiedException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Node({"User", "SocialUser"})
public class User {
    @Id
    private String id;

    private String nickname;

    private String email;

    private UserStatus status;

    public User(String nickname, String email, UserStatus userStatus) {
        this.id = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.email = email;
        this.status = userStatus;
    }

    public void verify() {
        if(status!=UserStatus.UNVERIFIED) {
            throw new NotUnverifiedException(this.id);
        }
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
package com.example.GooRoomBe.account.auth.domain;

import com.example.GooRoomBe.account.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;


@Getter
@Node({"LocalAuth", "Auth"})
public class LocalAuth extends Auth {

    private String password;

    public LocalAuth(User user, String password) {
        super(user);
        this.password = password;
    }

    public void updatePassword(String password, String userId) {
        if(!userId.equals(this.user.getId())) {
            throw new IllegalArgumentException("Invalid userId");
        }

        this.password = password;
    }
}
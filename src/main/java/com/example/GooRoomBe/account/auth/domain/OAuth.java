package com.example.GooRoomBe.account.auth.domain;

import com.example.GooRoomBe.account.user.domain.User;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.neo4j.core.schema.Node;

@Node({"OAuth", "Auth"})
@Getter
public class OAuth extends Auth {

    private final Provider provider;
    private final String providerId;

    @Builder
    public OAuth(User user, Provider provider, String providerId) {
        super(user);
        this.provider = provider;
        this.providerId = providerId;
    }
}
package com.example.GooRoomBe.account.auth.domain;

import com.example.GooRoomBe.account.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.UUID;

@Getter
@Node("Auth")
public abstract class Auth {

    @Id
    protected String id;

    @Relationship(type = "GUARANTEES", direction = Relationship.Direction.OUTGOING)
    protected User user;

    public Auth(User user) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
    }
}
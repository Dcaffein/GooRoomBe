package com.example.GooRoomBe.account.auth.domain.token;

import com.example.GooRoomBe.account.auth.exception.TokenTheftDetectedException;
import com.example.GooRoomBe.account.user.domain.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

@Node
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    @Relationship(type = "ISSUED_TO", direction = Relationship.Direction.OUTGOING)
    private User user;

    private String tokenValue;

    public RefreshToken(User user, String tokenValue) {
        this.user = user;
        this.tokenValue = tokenValue;
    }

    public void updateTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public void rotate(String incomingToken, String newToken) {
        if (!this.tokenValue.equals(incomingToken)) {
            throw new TokenTheftDetectedException();
        }
        this.tokenValue = newToken;
    }
}

package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RelationshipProperties
public class FriendRecognition {
    @RelationshipId
    @GeneratedValue
    private Long id;

    @TargetNode
    @Getter
    private SocialUser user;

    @Getter
    private boolean onIntroduce;

    private String friendAlias;

    private Double weight;

    public FriendRecognition(SocialUser user) {
        this.user = user;
        this.friendAlias = null;
        this.onIntroduce = false;
    }

    public void updateFriendAlias(String newAlias) {
        this.friendAlias = newAlias;
    }

    public void updateOnIntroduce(boolean onIntroduce) {
        this.onIntroduce = onIntroduce;
    }

    public String getFriendAlias() {
        return friendAlias == null ? "" : friendAlias;
    }
}

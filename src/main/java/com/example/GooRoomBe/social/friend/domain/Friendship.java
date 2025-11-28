package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.friend.exception.FriendshipAuthorizationException;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.MEMBER_OF;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Node("FriendShip")
public class Friendship {
    @Getter
    @Id
    private String id;

    @Getter
    private LocalDate createdAt;

    @Relationship(type = MEMBER_OF, direction = Relationship.Direction.INCOMING)
    private List<FriendRecognition> recognitions = new ArrayList<>();

    public Friendship(SocialUser userA, SocialUser userB) {
        this.id = generateCompositeId(userA.getId(), userB.getId());
        this.createdAt = LocalDate.now();

        this.recognitions.add(new FriendRecognition(userA));
        this.recognitions.add(new FriendRecognition(userB));
    }

    @PersistenceCreator
    public Friendship(String id, LocalDate createdAt, List<FriendRecognition> recognitions) {
        this.id = id;
        this.createdAt = createdAt;
        this.recognitions = recognitions;
    }

    private FriendRecognition getSelfRecognition(String userId) {
        return this.recognitions.stream()
                .filter(friendRecognition -> friendRecognition.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new FriendshipNotFoundException(this.id, userId));
    }

    private FriendRecognition getFriendRecognition(String userId) {
        boolean isMember = this.recognitions.stream()
                .anyMatch(friendRecognition -> friendRecognition.getUser().getId().equals(userId));

        if (!isMember) {
            throw new FriendshipNotFoundException(this.id, userId);
        }

        return this.recognitions.stream()
                .filter(friendRecognition -> !friendRecognition.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Friendship data corrupted: No friend found"));
    }

    private static String generateCompositeId(String id1, String id2) {
        return Stream.of(id1, id2)
                .sorted()
                .collect(Collectors.joining("_"));
    }

    public Set<SocialUser> getUsers() {
        return this.recognitions.stream()
                .map(FriendRecognition::getUser)
                .collect(Collectors.toSet());
    }

    public String getFriendAlias(String userId) {
        return getSelfRecognition(userId).getFriendAlias();
    }

    public void updateFriendAlias(String userId, String alias) {
        getSelfRecognition(userId).updateFriendAlias(alias);
    }

    public void updateOnIntroduce(String userId, boolean onIntroduce) {
        getSelfRecognition(userId).updateOnIntroduce(onIntroduce);
    }

    public SocialUser getFriend(String userId) {
        return getFriendRecognition(userId).getUser();
    }

    public void checkDeletable(String userId) {
        boolean isParticipant = this.recognitions.stream()
                .anyMatch(r -> r.getUser().getId().equals(userId));

        if (!isParticipant) {
            throw new FriendshipAuthorizationException(userId);
        }
    }
}
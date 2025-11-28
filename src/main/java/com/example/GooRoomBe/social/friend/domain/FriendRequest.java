package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.friend.domain.event.FriendRequestAcceptedEvent;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.SENT;
import static com.example.GooRoomBe.social.common.SocialSchemaConstants.TO;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Node("FriendRequest")
public class FriendRequest extends AbstractAggregateRoot<FriendRequest> {
    @Id
    private String id;

    @Relationship(type = SENT, direction = Relationship.Direction.INCOMING)
    private SocialUser requester;

    @Relationship(type = TO, direction = Relationship.Direction.OUTGOING)
    private SocialUser receiver;

    private LocalDateTime createdAt;

    private FriendRequestStatus status;

    public FriendRequest(SocialUser requester, SocialUser receiver) {
        this.id = UUID.randomUUID().toString();
        this.requester = requester;
        this.receiver = receiver;
        this.status = FriendRequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(FriendRequestStatus newStatus, String currentUserId) {
        this.status.transit(this, newStatus, currentUserId);
    }

    void completeAcceptance() {
        this.status = FriendRequestStatus.ACCEPTED;

        this.registerEvent(new FriendRequestAcceptedEvent(
                this.id,
                this.requester.getId(),
                this.receiver.getId()
        ));
    }

    public void checkCancelable(String currentUserId) {
        this.status.validateCancel(this, currentUserId);
    }

    void setStatus(FriendRequestStatus status) {
        this.status = status;
    }

    public boolean isAccepted() {
        return this.status == FriendRequestStatus.ACCEPTED;
    }
}
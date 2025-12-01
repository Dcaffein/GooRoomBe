package com.example.GooRoomBe.social.friend.domain.factory;

import com.example.GooRoomBe.social.friend.infrastructure.FriendshipPort;
import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.AlreadyFriendException;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotAcceptedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendshipFactory {

    private final FriendshipPort friendshipPort;

    public Friendship createFromRequest(FriendRequest friendRequest) {
        if (!friendRequest.isAccepted()) {
            throw new FriendRequestNotAcceptedException(friendRequest.getId());
        }

        if (friendshipPort.existsFriendshipBetween(
                friendRequest.getRequester().getId(),
                friendRequest.getReceiver().getId())) {
            throw new AlreadyFriendException(friendRequest.getRequester().getId(), friendRequest.getReceiver().getId());
        }

        return new Friendship(
                friendRequest.getRequester(),
                friendRequest.getReceiver()
        );
    }
}
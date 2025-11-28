package com.example.GooRoomBe.social.friend.application;

import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.domain.event.FriendRequestAcceptedEvent;
import com.example.GooRoomBe.social.friend.domain.factory.FriendshipFactory;
import com.example.GooRoomBe.social.friend.infrastructure.FriendRequestRepository;
import com.example.GooRoomBe.social.friend.infrastructure.FriendshipRepository;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FriendshipEventListener {

    private final FriendshipFactory friendshipFactory;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;

    @EventListener
    @Transactional
    public void handleFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        FriendRequest friendRequest = friendRequestRepository.findById(event.requestId())
                .orElseThrow(() -> new FriendRequestNotFoundException(event.requestId()));

        Friendship friendship = friendshipFactory.createFromRequest(friendRequest);

        friendshipRepository.save(friendship);
    }
}
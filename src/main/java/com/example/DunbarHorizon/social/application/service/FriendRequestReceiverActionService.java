package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.port.in.FriendRequestReceiverActionUseCase;
import com.example.DunbarHorizon.social.domain.friend.event.FriendRequestAcceptedEvent;
import com.example.DunbarHorizon.social.domain.friend.event.FriendshipCreatedEvent;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestNotFoundException;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import com.example.DunbarHorizon.social.domain.friend.Friendship;
import com.example.DunbarHorizon.social.domain.friend.FriendshipBroker;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendRequestRepository;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Neo4jTransactional
public class FriendRequestReceiverActionService implements FriendRequestReceiverActionUseCase {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendshipBroker friendshipBroker;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void updateStatus(Long receiverId, Long counterpartId, FriendRequestStatus status) {
        String requestId = FriendRequest.generateId(receiverId, counterpartId);
        FriendRequest request = findRequestById(requestId);
        request.updateStatus(receiverId, status);

        if (request.isAccepted()) {
            acceptRequest(request, requestId);
        } else {
            friendRequestRepository.updateStatus(request);
        }
    }

    private void acceptRequest(FriendRequest request, String requestId) {
        Friendship friendship = friendshipBroker.createFrom(request);
        friendshipRepository.save(friendship);
        friendRequestRepository.deleteById(requestId);

        eventPublisher.publishEvent(new FriendshipCreatedEvent(
                request.getRequester().getId(),
                request.getReceiver().getId()
        ));
        eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
                request.getRequester().getId(),
                request.getReceiver().getId(),
                request.getReceiver().getNickname()
        ));
    }

    private FriendRequest findRequestById(String requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException(requestId));
    }
}

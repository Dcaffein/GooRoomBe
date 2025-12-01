package com.example.GooRoomBe.social.friend.domain.service;

import com.example.GooRoomBe.social.friend.infrastructure.FriendshipPort;
import com.example.GooRoomBe.social.friend.exception.AlreadyFriendException;
import com.example.GooRoomBe.social.friend.exception.DuplicateFriendRequestException;
import com.example.GooRoomBe.social.friend.infrastructure.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendRequestDuplicationValidator {
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipPort friendshipPort;

    public void validateNewRequest(String requesterId, String receiverId) {
        checkRequestDuplication(requesterId, receiverId);
        checkFriendShipDuplication(requesterId, receiverId);
    }

    private void checkFriendShipDuplication(String requesterId, String receiverId) {
        if (friendshipPort.existsFriendshipBetween(requesterId, receiverId)) {
            throw new AlreadyFriendException(requesterId,receiverId);
        }
    }

    private void checkRequestDuplication(String requesterId, String receiverId) {
        if (friendRequestRepository.existsRequestBetween(requesterId, receiverId)) {
            throw new DuplicateFriendRequestException(requesterId,receiverId);
        }
    }
}
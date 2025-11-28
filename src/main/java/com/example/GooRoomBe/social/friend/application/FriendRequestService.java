package com.example.GooRoomBe.social.friend.application;

import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.FriendRequestStatus;
import com.example.GooRoomBe.social.friend.domain.factory.FriendRequestFactory;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotFoundException;
import com.example.GooRoomBe.social.friend.infrastructure.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@Service
public class FriendRequestService {
    private final FriendRequestRepository friendRequestRepository;
    private final FriendRequestFactory friendRequestFactory;

    @Transactional
    public FriendRequest createFriendRequest(String requesterId, String receiverId) {
        FriendRequest newFriendRequest = friendRequestFactory.create(requesterId, receiverId);
        friendRequestRepository.save(newFriendRequest);
        return newFriendRequest;
    }


    @Transactional
    public void updateFriendRequest(String requestId, String currentUserId, FriendRequestStatus newStatus) {
        FriendRequest friendRequest = findFriendRequestById(requestId);
        friendRequest.updateStatus(newStatus, currentUserId);
        friendRequestRepository.save(friendRequest);
    }

    @Transactional
    public void cancelFriendRequest(String requestId, String currentUserId) {
        FriendRequest friendRequest = findFriendRequestById(requestId);

        friendRequest.checkCancelable(currentUserId);
    }

    private FriendRequest findFriendRequestById(String requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException(requestId));
    }
}

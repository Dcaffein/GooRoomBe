package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.port.in.FriendRequesterActionUseCase;
import com.example.DunbarHorizon.social.domain.friend.exception.DuplicateFriendRequestException;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestNotFoundException;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendRequestRepository;
import com.example.DunbarHorizon.social.domain.friend.FriendshipBroker;
import com.example.DunbarHorizon.social.domain.socialUser.repository.SocialUserRepository;
import com.example.DunbarHorizon.social.domain.socialUser.UserReference;
import com.example.DunbarHorizon.social.domain.socialUser.exception.UserReferenceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Neo4jTransactional
public class FriendRequestRequesterActionService implements FriendRequesterActionUseCase {
    private final FriendRequestRepository friendRequestRepository;
    private final SocialUserRepository socialUserRepository;
    private final FriendshipBroker friendshipBroker;

    @Override
    public FriendRequest sendRequest(Long requesterId, Long receiverId) {
        UserReference requester = socialUserRepository.findById(requesterId)
                .orElseThrow(() -> new UserReferenceNotFoundException(requesterId));
        UserReference receiver = socialUserRepository.findById(receiverId)
                .orElseThrow(() -> new UserReferenceNotFoundException(receiverId));

        FriendRequest newRequest = friendshipBroker.propose(requester, receiver);

        try {
            friendRequestRepository.saveRequest(newRequest);
            return newRequest;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFriendRequestException(requesterId, receiverId);
        }
    }

    @Override
    public void cancelRequest(Long counterpartId, Long requesterId) {
        String requestId = FriendRequest.generateId(requesterId, counterpartId);
        FriendRequest request = findRequestById(requestId);
        request.cancel(requesterId);
        friendRequestRepository.deleteById(requestId);
    }


    private FriendRequest findRequestById(String requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException(requestId));
    }
}

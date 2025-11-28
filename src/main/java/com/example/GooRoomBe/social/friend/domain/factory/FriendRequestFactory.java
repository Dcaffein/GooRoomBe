package com.example.GooRoomBe.social.friend.domain.factory;

import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.service.FriendRequestDuplicationValidator;
import com.example.GooRoomBe.social.friend.exception.CannotRequestToSelfException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FriendRequestFactory {
    private final SocialUserRepository socialUserRepository;
    private final FriendRequestDuplicationValidator friendRequestDuplicationValidator;

    @Autowired
    public FriendRequestFactory(SocialUserRepository socialUserRepository, FriendRequestDuplicationValidator friendRequestDuplicationValidator) {
        this.socialUserRepository = socialUserRepository;
        this.friendRequestDuplicationValidator = friendRequestDuplicationValidator;
    }

    public FriendRequest create(String requesterId, String receiverId) {
        if(requesterId.equals(receiverId)) {
            throw new CannotRequestToSelfException(requesterId);
        }

        SocialUser requester = socialUserRepository.getUser(requesterId);
        SocialUser receiver = socialUserRepository.getUser(receiverId);
        friendRequestDuplicationValidator.validateNewRequest(requesterId, receiverId);

        return new FriendRequest(requester, receiver);
    }
}

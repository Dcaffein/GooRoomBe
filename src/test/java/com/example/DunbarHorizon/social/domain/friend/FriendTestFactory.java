package com.example.DunbarHorizon.social.domain.friend;

import com.example.DunbarHorizon.social.domain.socialUser.UserReference;

public class FriendTestFactory {

    public static FriendRequest createRequest(UserReference req, UserReference res) {
        return new FriendRequest(req, res);
    }

    public static FriendRequest createAcceptedRequest(UserReference req, UserReference res) {
        FriendRequest request = new FriendRequest(req, res);
        request.updateStatus(res.getId(), FriendRequestStatus.ACCEPTED);
        return request;
    }

    public static Friendship createFriendship(UserReference userA, UserReference userB) {
        return new Friendship(userA, userB);
    }

    public static Friendship createFriendshipWithIntimacy(UserReference userA, UserReference userB, double intimacyDelta) {
        Friendship friendship = new Friendship(userA, userB);
        friendship.adjustInterestScore(userA.getId(), intimacyDelta);
        return friendship;
    }
}

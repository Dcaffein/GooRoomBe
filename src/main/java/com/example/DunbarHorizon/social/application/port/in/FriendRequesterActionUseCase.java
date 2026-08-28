package com.example.DunbarHorizon.social.application.port.in;

import com.example.DunbarHorizon.social.domain.friend.FriendRequest;

public interface FriendRequesterActionUseCase {
    FriendRequest sendRequest(Long requesterId, Long receiverId);
    void cancelRequest(Long counterpartId, Long requesterId);
}

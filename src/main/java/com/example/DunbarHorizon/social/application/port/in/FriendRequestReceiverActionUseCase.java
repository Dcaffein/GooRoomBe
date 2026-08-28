package com.example.DunbarHorizon.social.application.port.in;

import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;

public interface FriendRequestReceiverActionUseCase {
    void updateStatus(Long receiverId, Long counterpartId, FriendRequestStatus status);
}

package com.example.DunbarHorizon.social.application.port.in;

import com.example.DunbarHorizon.social.application.dto.result.FriendRequestResult;
import com.example.DunbarHorizon.social.application.dto.FriendRequestDirection;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;

import java.util.List;

public interface FriendRequestQueryUseCase {
    List<FriendRequestResult> getRequests(
            Long userId, FriendRequestDirection direction, FriendRequestStatus status);
}

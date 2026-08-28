package com.example.DunbarHorizon.social.adapter.in.web.dto;

import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import jakarta.validation.constraints.NotNull;

public record FriendRequestStatusUpdateRequest(
        @NotNull(message = "상태(status)는 필수입니다.")
        FriendRequestStatus status
) {}

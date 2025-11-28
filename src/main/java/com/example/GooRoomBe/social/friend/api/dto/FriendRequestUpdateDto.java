package com.example.GooRoomBe.social.friend.api.dto;

import com.example.GooRoomBe.social.friend.domain.FriendRequestStatus;
import jakarta.validation.constraints.NotNull;

public record FriendRequestUpdateDto(
        @NotNull(message = "변경할 상태값(status)은 필수입니다.")
        FriendRequestStatus status
) {}
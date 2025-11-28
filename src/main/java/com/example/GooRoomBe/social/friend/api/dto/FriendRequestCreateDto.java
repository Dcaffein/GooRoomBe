package com.example.GooRoomBe.social.friend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FriendRequestCreateDto(
        @NotBlank(message = "친구 요청을 보낼 대상(receiverId)은 필수입니다.")
        String receiverId
) {}
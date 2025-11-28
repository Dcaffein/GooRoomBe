package com.example.GooRoomBe.social.friend.api.dto;

import jakarta.validation.constraints.Size;

public record FriendUpdateRequestDto(
        @Size(max = 20, message = "별명은 20자 이내여야 합니다.")
        String friendAlias,
        Boolean onIntroduce
) { }

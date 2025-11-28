package com.example.GooRoomBe.social.friend.api.dto;

import com.example.GooRoomBe.social.common.dto.SocialMemberResponseDto;
import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.FriendRequestStatus;

import java.time.LocalDateTime;

public record FriendRequestResponseDto(
        String id,
        SocialMemberResponseDto receiver,
        FriendRequestStatus status,
        LocalDateTime createdAt
) {
    public static FriendRequestResponseDto from(FriendRequest entity) {
        return new FriendRequestResponseDto(
                entity.getId(),
                SocialMemberResponseDto.from(entity.getRequester()),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
package com.example.GooRoomBe.social.friend.domain.event;

public record FriendRequestAcceptedEvent(String requestId, String requesterId, String receiverId) {
}

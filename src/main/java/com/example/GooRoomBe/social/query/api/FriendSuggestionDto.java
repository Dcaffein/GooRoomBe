package com.example.GooRoomBe.social.query.api;

public record FriendSuggestionDto(
        String suggestedFriendId,
        String suggestedFriendName,
        String commonFriendId
) {}
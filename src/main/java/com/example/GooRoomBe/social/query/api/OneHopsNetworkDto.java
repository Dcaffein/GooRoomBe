package com.example.GooRoomBe.social.query.api;

import java.util.Set;

public record OneHopsNetworkDto(
        String friendId,
        String friendName,
        String friendAlias,
        Set<String> mutualFriendIds
) {}
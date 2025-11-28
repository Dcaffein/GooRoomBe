package com.example.GooRoomBe.social.friend.application;

import com.example.GooRoomBe.social.friend.domain.Friendship;

public interface FriendshipPort {
    Friendship getFriendship(String myId, String friendId);

    Friendship save(Friendship friendship);

    void delete(Friendship friendship);
}
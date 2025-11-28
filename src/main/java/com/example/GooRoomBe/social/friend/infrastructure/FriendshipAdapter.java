package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.application.FriendshipPort;
import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendshipAdapter implements FriendshipPort {

    private final FriendshipRepository friendshipRepository;

    @Override
    public Friendship getFriendship(String myId, String friendId) {
        return friendshipRepository.findFriendshipByUsers(myId, friendId)
                .orElseThrow(() -> new FriendshipNotFoundException(myId, friendId));
    }

    @Override
    public Friendship save(Friendship friendship) {
        return friendshipRepository.save(friendship);
    }

    @Override
    public void delete(Friendship friendship) {
        friendshipRepository.delete(friendship);
    }
}
package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

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

    @Override
    public boolean existsFriendshipBetween(String requesterId, String receiverId) {
        return friendshipRepository.existsFriendshipBetween(requesterId, receiverId);
    }

    @Override
    public Set<Friendship> filterFriendsFromIdList(String ownerId, List<String> potentialMemberIds) {
        return friendshipRepository.filterFriendsFromIdList(ownerId, potentialMemberIds);
    }
}
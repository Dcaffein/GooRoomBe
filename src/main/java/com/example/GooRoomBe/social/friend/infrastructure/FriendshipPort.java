package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface FriendshipPort {
    Friendship getFriendship(String myId, String friendId);

    Friendship save(Friendship friendship);

    void delete(Friendship friendship);

    boolean existsFriendshipBetween(@Param("requesterId") String requesterId, @Param("receiverId") String receiverId);
    Set<Friendship> filterFriendsFromIdList(@Param("ownerId") String ownerId, @Param("potentialMemberIds") List<String> potentialMemberIds);
}
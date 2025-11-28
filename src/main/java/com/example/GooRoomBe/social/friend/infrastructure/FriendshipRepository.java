package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;

public interface FriendshipRepository extends Neo4jRepository<Friendship, String> {
    @Query("RETURN EXISTS(" +
            "(:" + SOCIAL_USER + " {id: $requesterId})-[:" + MEMBER_OF + "]->" +
            "(:" + FRIENDSHIP + ")" +
            "<-[:" + MEMBER_OF + "]-(:" + SOCIAL_USER + " {id: $receiverId})" +
            ")")
    boolean existsFriendshipBetween(@Param("requesterId") String requesterId, @Param("receiverId") String receiverId);


    @Query("MATCH " +
            "(owner:" + SOCIAL_USER + " {id: $ownerId})-[:" + MEMBER_OF + "]->" +
            "(fs:" + FRIENDSHIP + ")" +
            "<-[:" + MEMBER_OF + "]-(friend:" + SOCIAL_USER + ") " +
            "WHERE friend.id IN $potentialMemberIds " +
            "RETURN fs")
    Set<Friendship> filterFriendsFromIdList(@Param("ownerId") String ownerId, @Param("potentialMemberIds") List<String> potentialMemberIds);

    @Query("MATCH (u1:" +SOCIAL_USER+ " {id: $myId})-[r1:" +MEMBER_OF+ "]->" +
            "(f:" + FRIENDSHIP + ")" +
            "<-[r2:" +MEMBER_OF+ "]-(u2:" +SOCIAL_USER+ " {id: $friendId}) " +
            "RETURN f, collect(r1), collect(u1), collect(r2), collect(u2)")
    Optional<Friendship> findFriendshipByUsers(@Param("myId") String myId, @Param("friendId") String friendId);
}

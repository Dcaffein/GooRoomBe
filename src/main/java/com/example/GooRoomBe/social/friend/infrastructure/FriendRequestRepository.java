package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;

public interface FriendRequestRepository extends Neo4jRepository<FriendRequest, String> {
    @Query("RETURN EXISTS(" +
            "(:"+SOCIAL_USER+ " {id: $requesterId})" +
            "-[:" +SENT+ "]-> (:" + FRIEND_REQUEST + ") -[:" +TO+ "]->" +
            "(:"+SOCIAL_USER+ " {id: $receiverId})" +
            ")")
    boolean existsRequestBetween(@Param("requesterId") String requesterId, @Param("receiverId") String receiverId);
}

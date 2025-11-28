package com.example.GooRoomBe.social.friend.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
class FriendRequestRepositoryTest {

    @Container
    @ServiceConnection
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5");

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    private final String REQUESTER_ID = "user-A";
    private final String RECEIVER_ID = "user-B";
    private final String STRANGER_ID = "user-C";

    @BeforeEach
    void setUp() {
        friendRequestRepository.deleteAll();
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        //  (SocialUser) -[SENT]-> (FriendRequest) -[TO]-> (SocialUser)
        String cypher = String.format("""
            CREATE (req:%s {id: $reqId})
            CREATE (recv:%s {id: $recvId})
            CREATE (stranger:%s {id: $strangerId})
            
            CREATE (fr:%s {id: 'fr-1', status: 'PENDING'})
            
            CREATE (req)-[:%s]->(fr)-[:%s]->(recv)
            """,
                SOCIAL_USER, SOCIAL_USER, SOCIAL_USER,
                FRIEND_REQUEST,
                SENT, TO
        );

        neo4jClient.query(cypher)
                .bindAll(Map.of(
                        "reqId", REQUESTER_ID,
                        "recvId", RECEIVER_ID,
                        "strangerId", STRANGER_ID
                ))
                .run();
    }

    @Test
    @DisplayName("exists: 요청자(A) -> 수신자(B) 방향의 친구 요청이 존재하면 true를 반환한다")
    void existsRequestBetween_ShouldReturnTrue_WhenExists() {
        // When
        boolean exists = friendRequestRepository.existsRequestBetween(REQUESTER_ID, RECEIVER_ID);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("exists: 친구 요청이 없는 관계(A -> C)라면 false를 반환한다")
    void existsRequestBetween_ShouldReturnFalse_WhenNotExists() {
        // When
        boolean exists = friendRequestRepository.existsRequestBetween(REQUESTER_ID, STRANGER_ID);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("exists: 화살표 방향이 반대(B -> A)인 경우 false를 반환한다")
    void existsRequestBetween_ShouldCheckDirection() {
        // When
        boolean exists = friendRequestRepository.existsRequestBetween(RECEIVER_ID, REQUESTER_ID);

        // Then
        assertThat(exists).isFalse();
    }
}
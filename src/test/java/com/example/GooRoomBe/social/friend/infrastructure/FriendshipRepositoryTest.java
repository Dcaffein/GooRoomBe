package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.Friendship;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
class FriendshipRepositoryTest {

    @Container
    @ServiceConnection
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5");

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    private String userAId;
    private String userBId;
    private String userCId;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        userAId = "userA";
        userBId = "userB";
        userCId = "userC";

        String cypher = String.format("""
            CREATE (a:%s {id: $idA}), (b:%s {id: $idB}), (c:%s {id: $idC})
            CREATE (fs:%s {id: 'fs_AB'})
            CREATE (a)-[:%s]->(fs)<-[:%s]-(b)
            """,
                SOCIAL_USER, SOCIAL_USER, SOCIAL_USER,
                FRIENDSHIP,
                MEMBER_OF, MEMBER_OF
        );

        neo4jClient.query(cypher)
                .bindAll(Map.of("idA", userAId, "idB", userBId, "idC", userCId))
                .run();
    }

    @Test
    @DisplayName("exists: 친구 관계가 존재하면 true를 반환한다")
    void existsFriendshipBetween_ShouldReturnTrue_WhenExists() {
        // when
        boolean exists = friendshipRepository.existsFriendshipBetween(userAId, userBId);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("exists: 친구 관계가 없으면 false를 반환한다")
    void existsFriendshipBetween_ShouldReturnFalse_WhenNotExists() {
        // when
        boolean exists = friendshipRepository.existsFriendshipBetween(userAId, userCId);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("find: 친구 ID로 Friendship 엔티티를 조회한다")
    void findFriendshipByUsers_ShouldReturnEntity() {
        // when
        Optional<Friendship> result = friendshipRepository.findFriendshipByUsers(userAId, userBId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("fs_AB");
    }

    @Test
    @DisplayName("filter: ID 리스트 중 실제 친구 관계인 것만 필터링한다")
    void filterFriendsFromIdList_ShouldReturnOnlyFriends() {
        // given
        List<String> candidates = List.of(userBId, userCId, "unknownUser");

        // when
        Set<Friendship> result = friendshipRepository.filterFriendsFromIdList(userAId, candidates);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getId()).isEqualTo("fs_AB");
    }

    @Test
    @DisplayName("updateAlias: 별명을 수정하고 저장하면 DB의 엣지 속성(Edge Property)이 업데이트된다")
    void updateFriendAlias_ShouldPersistEdgeProperties() {
        friendshipRepository.deleteAll();
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String initCypher = String.format("""
            CREATE (a:%s {id: $idA}), (b:%s {id: $idB})
            CREATE (fs:%s {id: $fsId})
            CREATE (a)-[:%s {friendAlias: '초기별명', onIntroduce: false}]->(fs)
            CREATE (b)-[:%s]->(fs)
            """,
                SOCIAL_USER, SOCIAL_USER,
                FRIENDSHIP,
                MEMBER_OF, MEMBER_OF
        );

        neo4jClient.query(initCypher)
                .bindAll(Map.of("idA", userAId, "idB", userBId, "fsId", "fs_AB"))
                .run();

        Friendship friendship = friendshipRepository.findById("fs_AB").orElseThrow();
        friendship.updateFriendAlias(userAId, "찐친");

        friendshipRepository.save(friendship);

        String verifyCypher = String.format("""
                MATCH (u:%s {id: $uid})-[r:%s]->(fs:%s {id: $fsId})
                RETURN r.friendAlias
                """,
                SOCIAL_USER, MEMBER_OF, FRIENDSHIP
        );

        String savedAlias = neo4jClient.query(verifyCypher)
                .bindAll(Map.of("uid", userAId, "fsId", "fs_AB"))
                .fetchAs(String.class)
                .one()
                .orElseThrow();

        assertThat(savedAlias).isEqualTo("찐친");
    }
}
package com.example.GooRoomBe.social.query;

import com.example.GooRoomBe.social.query.api.FriendSuggestionDto;
import com.example.GooRoomBe.social.query.api.OneHopsNetworkDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
@Import(SocialQueryService.class) // Service Bean 등록
class SocialQueryServiceTest {

    @Container
    @ServiceConnection
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5");

    @Autowired
    private SocialQueryService socialQueryService;

    @Autowired
    private Neo4jClient neo4jClient;

    private final String MY_ID = "me";

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    @DisplayName("1-Hop 조회: 친구 목록과 '함께 아는 친구(Mutual)' ID가 정확히 집계되어야 한다")
    void getOneHopsNetwork_ShouldReturnFriendsAndMutuals() {
        // Given: 삼각형 관계 (Me - Friend1 - Friend2 - Me)
        // Me는 F1, F2와 친구 / F1과 F2도 서로 친구
        String cypher = String.format("""
            CREATE (me:%s {id: $myId, nickname: '나'})
            CREATE (f1:%s {id: 'f1', nickname: '친구1'})
            CREATE (f2:%s {id: 'f2', nickname: '친구2'})
            CREATE (loner:%s {id: 'loner', nickname: '왕따친구'})

            // Me <-> F1 (friendAlias: 베프)
            CREATE (me)-[:%s {friendAlias: '베프'}]->(:%s)<-[:%s]-(f1)
            
            // Me <-> F2
            CREATE (me)-[:%s]->(:%s)<-[:%s]-(f2)
            
            // Me <-> Loner (공통 친구 없음)
            CREATE (me)-[:%s]->(:%s)<-[:%s]-(loner)

            // F1 <-> F2 (Mutual 관계 형성)
            CREATE (f1)-[:%s]->(:%s)<-[:%s]-(f2)
            """,
                SOCIAL_USER, SOCIAL_USER, SOCIAL_USER,SOCIAL_USER, // Nodes
                MEMBER_OF, FRIENDSHIP, MEMBER_OF, // Me-F1
                MEMBER_OF, FRIENDSHIP, MEMBER_OF, // Me-F2
                MEMBER_OF, FRIENDSHIP, MEMBER_OF, // Me-Loner
                MEMBER_OF, FRIENDSHIP, MEMBER_OF  // F1-F2
        );

        neo4jClient.query(cypher).bindAll(Map.of("myId", MY_ID)).run();

        // When
        List<OneHopsNetworkDto> result = socialQueryService.getOneHopsNetwork(MY_ID);

        // Then
        assertThat(result).hasSize(3); // F1, F2, Loner

        // 1. Friend1 검증 (별명, 함께 아는 친구 F2 존재 확인)
        OneHopsNetworkDto f1 = result.stream().filter(d -> d.friendId().equals("f1")).findFirst().get();
        assertThat(f1.friendAlias()).isEqualTo("베프");
        assertThat(f1.mutualFriendIds()).contains("f2");

        // 2. Loner 검증 (함께 아는 친구 없음)
        OneHopsNetworkDto loner = result.stream().filter(d -> d.friendId().equals("loner")).findFirst().get();
        assertThat(loner.mutualFriendIds()).isEmpty();
    }

    @Test
    @DisplayName("2-Hop 추천: 조건(onIntroduce=true, exposure=true)을 만족하는 경우에만 추천된다")
    void getTwoHopSuggestion_Success() {
        // Given
        // Me -> Pivot(onIntroduce=true) -> Label(exposure=true) -> Target
        String cypher = String.format("""
            CREATE (me:%s {id: $myId})
            CREATE (pivot:%s {id: 'pivot'})
            CREATE (target:%s {id: 'target', nickname: '추천대상'})

            // Me <-> Pivot (onIntroduce: true)
            CREATE (me)-[:%s {onIntroduce: true}]->(:%s)<-[:%s]-(pivot)

            // Pivot -> Label(Public) -> Target
            CREATE (l:%s {labelName: '공개그룹', exposure: true})
            CREATE (pivot)-[:%s]->(l)-[:%s]->(target)
            CREATE (l)-[:%s]->(me)
            """,
                SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,
                MEMBER_OF, FRIENDSHIP, MEMBER_OF,
                LABEL,
                OWNS, HAS_MEMBER,
                HAS_MEMBER
        );
        neo4jClient.query(cypher).bindAll(Map.of("myId", MY_ID)).run();

        // When
        List<FriendSuggestionDto> result = socialQueryService.getTwoHopSuggestion(MY_ID);

        // Then
        assertThat(result).hasSize(1);
        FriendSuggestionDto suggestion = result.getFirst();
        assertThat(suggestion.suggestedFriendId()).isEqualTo("target");
        assertThat(suggestion.commonFriendId()).isEqualTo("pivot");
    }

    @Test
    @DisplayName("2-Hop 추천 필터링: 조건이 맞지 않으면 추천되지 않아야 한다")
    void getTwoHopSuggestion_Filtering_Test() {
        // Given
        // 1. Case A: onIntroduce = false
        // 2. Case B: Label exposure = false
        // 3. Case C: 이미 친구인 경우
        String cypher = String.format("""
            CREATE (me:%s {id: $myId})
            CREATE (p1:%s {id: 'p1'}) // onIntroduce false
            CREATE (p2:%s {id: 'p2'}) // Label private
            CREATE (p3:%s {id: 'p3'}) // Already friend
            
            CREATE (t1:%s {id: 't1'})
            CREATE (t2:%s {id: 't2'})
            CREATE (t3:%s {id: 't3'}) // 나랑 이미 친구

            // Case A: 소개 비허용
            CREATE (me)-[:%s {onIntroduce: false}]->(:%s)<-[:%s]-(p1)
            CREATE (p1)-[:%s]->(:%s {exposure: true})-[:%s]->(t1)

            // Case B: 라벨 비공개
            CREATE (me)-[:%s {onIntroduce: true}]->(:%s)<-[:%s]-(p2)
            CREATE (p2)-[:%s]->(:%s {exposure: false})-[:%s]->(t2)

            // Case C: 이미 친구임 (t3와 me는 친구)
            CREATE (me)-[:%s {onIntroduce: true}]->(:%s)<-[:%s]-(p3)
            CREATE (p3)-[:%s]->(:%s {exposure: true})-[:%s]->(t3)
            CREATE (me)-[:%s]->(:%s)<-[:%s]-(t3)
            """,
                SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,SOCIAL_USER,
                // Case A
                MEMBER_OF, FRIENDSHIP, MEMBER_OF,
                OWNS, LABEL, HAS_MEMBER,
                // Case B
                MEMBER_OF, FRIENDSHIP, MEMBER_OF,
                OWNS, LABEL, HAS_MEMBER,
                // Case C
                MEMBER_OF, FRIENDSHIP, MEMBER_OF,
                OWNS, LABEL, HAS_MEMBER,
                MEMBER_OF, FRIENDSHIP, MEMBER_OF
        );
        neo4jClient.query(cypher).bindAll(Map.of("myId", MY_ID)).run();

        // When
        List<FriendSuggestionDto> result = socialQueryService.getTwoHopSuggestion(MY_ID);

        // Then
        assertThat(result).isEmpty(); // 아무것도 추천되면 안 됨
    }

    @Test
    @DisplayName("Edge Case: 친구가 없는 사용자(Loner)가 1-Hop 네트워크를 조회하면 빈 리스트를 반환한다")
    void getOneHopsNetwork_Loner_ShouldReturnEmpty() {
        // Given
        String lonerId = "loner";
        // 관계(Edge) 없이 유저 노드만 생성
        String cypher = String.format("CREATE (u:%s {id: $id})", SOCIAL_USER);

        neo4jClient.query(cypher)
                .bindAll(Map.of("id", lonerId))
                .run();

        // When
        List<OneHopsNetworkDto> result = socialQueryService.getOneHopsNetwork(lonerId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty(); // 에러가 나지 않고 빈 리스트여야 함
    }

    @Test
    @DisplayName("Edge Case: 친구가 없는 사용자(Loner)가 친구 추천을 조회하면 빈 리스트를 반환한다")
    void getTwoHopSuggestion_Loner_ShouldReturnEmpty() {
        // Given
        String lonerId = "loner";
        // 유저 노드만 생성
        String cypher = String.format("CREATE (u:%s {id: $id})", SOCIAL_USER);

        neo4jClient.query(cypher)
                .bindAll(Map.of("id", lonerId))
                .run();

        // When
        List<FriendSuggestionDto> result = socialQueryService.getTwoHopSuggestion(lonerId);

        // Then
        // 1-hop 친구가 없으므로, 그 건너편에 있는 2-hop이나 라벨을 탐색할 수 없음 -> 결과 0건
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
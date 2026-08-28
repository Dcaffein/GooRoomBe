package com.example.DunbarHorizon.social.adapter.out;

import com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.SocialNetworkRepositoryAdapter;
import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import com.example.DunbarHorizon.support.Neo4jRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 그래프:
 *   me(1) ↔ A(10)[0.9], B(20)[0.8], C(30)[0.7], D(40)[0.6], E(50)[0.5], F(60)[0.1]
 *   A(10) ↔ B(20)[0.85]  — SUPPORT(5) 경계 안: A,B 모두 포함 → 항상 반환
 *   A(10) ↔ F(60)[0.4]   — SUPPORT(5) 경계 밖: F 제외 → SUPPORT에서는 미반환, DUNBAR에서는 반환
 *   targetX(100) ↔ B(20)[intimacy=0.6], C(30)[intimacy=0.4]
 *   label(test-label-id): me-A, me-B 포함
 */
@Neo4jRepositoryTest
@Import(SocialNetworkRepositoryAdapter.class)
class SocialNetworkRepositoryAdapterTest {

    @Autowired
    private SocialNetworkRepositoryAdapter repository;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setupGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        neo4jClient.query("""
                CREATE (me:UserReference {id: 1})
                CREATE (fa:UserReference {id: 10})
                CREATE (fb:UserReference {id: 20})
                CREATE (fc:UserReference {id: 30})
                CREATE (fd:UserReference {id: 40})
                CREATE (fe:UserReference {id: 50})
                CREATE (ff:UserReference {id: 60})
                CREATE (tx:UserReference {id: 100})

                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.7}]->(:Friendship {intimacy: 0.9})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fa)
                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.3}]->(:Friendship {intimacy: 0.8})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fb)
                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.7})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fc)
                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.6})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fd)
                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.5})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fe)
                CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.1})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(ff)

                CREATE (fa)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.85})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(fb)
                CREATE (fa)-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]->(:Friendship {intimacy: 0.4})<-[:HAS_FRIENDSHIP {isRoutable: true, interestScore: 0.0}]-(ff)

                CREATE (tx)-[:HAS_FRIENDSHIP {isRoutable: true,  interestScore: 0.0}]->(:Friendship {intimacy: 0.6})<-[:HAS_FRIENDSHIP {isRoutable: true,  interestScore: 0.0}]-(fb)
                CREATE (tx)-[:HAS_FRIENDSHIP {isRoutable: false, interestScore: 0.0}]->(:Friendship {intimacy: 0.4})<-[:HAS_FRIENDSHIP {isRoutable: true,  interestScore: 0.0}]-(fc)

                CREATE (lbl:Label {id: 'test-label-id'})
                CREATE (me)-[:OWNS_LABEL]->(lbl)
                CREATE (lbl)-[:ATTACHED_TO]->(fa)
                CREATE (lbl)-[:ATTACHED_TO]->(fb)
                """).run();
    }

    // ───────── Default Network Graph ─────────

    @Test
    @DisplayName("DUNBAR 크기로 조회 시 경계 내 모든 친구 노드를 반환한다 (6명)")
    void getDefaultNetworkGraph_DUNBAR_경계_내_모든_노드를_반환한다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(1L, DunbarCircle.DUNBAR, 5, 10);

        assertThat(result).hasSize(6);
        assertThat(result).extracting(NodeGraphResult::nodeId)
                .containsExactlyInAnyOrder(10L, 20L, 30L, 40L, 50L, 60L);
    }

    @Test
    @DisplayName("DUNBAR 크기로 조회 시 A-B, A-F 엣지가 양방향으로 포함된다 (총 4개)")
    void getDefaultNetworkGraph_DUNBAR_경계_내_엣지를_양방향으로_반환한다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(1L, DunbarCircle.DUNBAR, 5, 10);

        long totalEdges = result.stream().mapToLong(n -> n.edges().size()).sum();
        assertThat(totalEdges).isEqualTo(4);

        NodeGraphResult nodeA = result.stream().filter(n -> n.nodeId().equals(10L)).findFirst().orElseThrow();
        assertThat(nodeA.edges()).extracting(e -> e.friendId()).containsExactlyInAnyOrder(20L, 60L);
    }

    @Test
    @DisplayName("SUPPORT(5) 크기로 조회 시 F(60)가 경계에서 제외되고 A-F 엣지도 제외된다")
    void getDefaultNetworkGraph_SUPPORT_경계_밖의_친구와_엣지가_제외된다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(1L, DunbarCircle.SUPPORT, 5, 10);

        assertThat(result).hasSize(5);
        assertThat(result).extracting(NodeGraphResult::nodeId).doesNotContain(60L);

        long totalEdges = result.stream().mapToLong(n -> n.edges().size()).sum();
        assertThat(totalEdges).isEqualTo(2); // A→B, B→A
    }

    @Test
    @DisplayName("고립 노드(엣지 없는 친구)도 빈 edges 리스트로 반환된다")
    void getDefaultNetworkGraph_고립_노드도_빈_엣지로_반환된다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(1L, DunbarCircle.DUNBAR, 5, 10);

        // C(30), D(40), E(50)는 연결이 없는 고립 노드
        assertThat(result).filteredOn(n -> List.of(30L, 40L, 50L).contains(n.nodeId()))
                .allMatch(n -> n.edges().isEmpty());
    }

    @Test
    @DisplayName("interestScore가 노드별로 올바르게 반환된다")
    void getDefaultNetworkGraph_interestScore가_노드별로_올바르게_반환된다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(1L, DunbarCircle.DUNBAR, 5, 10);

        NodeGraphResult nodeA = result.stream().filter(n -> n.nodeId().equals(10L)).findFirst().orElseThrow();
        NodeGraphResult nodeB = result.stream().filter(n -> n.nodeId().equals(20L)).findFirst().orElseThrow();
        assertThat(nodeA.interestScore()).isEqualTo(0.7);
        assertThat(nodeB.interestScore()).isEqualTo(0.3);

        // A→B 엣지에서 B의 friendInterest
        nodeA.edges().stream().filter(e -> e.friendId().equals(20L)).findFirst()
                .ifPresent(e -> assertThat(e.friendInterest()).isEqualTo(0.3));
    }

    @Test
    @DisplayName("친구가 없는 유저는 빈 nodes를 반환한다")
    void getDefaultNetworkGraph_친구가_없는_유저는_빈_결과를_반환한다() {
        List<NodeGraphResult> result = repository.getDefaultNetworkGraph(999L, DunbarCircle.DUNBAR, 5, 10);

        assertThat(result).isEmpty();
    }

    // ───────── Label Network ─────────

    @Test
    @DisplayName("라벨 네트워크 조회 시 라벨 멤버만 노드로 반환하고 멤버 간 엣지를 포함한다")
    void getLabelCustomNetwork_라벨_멤버_노드와_엣지를_반환한다() {
        List<NodeGraphResult> result = repository.getLabelCustomNetwork(1L, "test-label-id", DunbarCircle.DUNBAR, 5, 10);

        // label에는 A(10), B(20)만 포함
        assertThat(result).hasSize(2);
        assertThat(result).extracting(NodeGraphResult::nodeId)
                .containsExactlyInAnyOrder(10L, 20L);

        // A-B 양방향 엣지
        long totalEdges = result.stream().mapToLong(n -> n.edges().size()).sum();
        assertThat(totalEdges).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 라벨 ID로 조회하면 빈 nodes를 반환한다")
    void getLabelCustomNetwork_존재하지_않는_라벨ID는_빈_결과를_반환한다() {
        List<NodeGraphResult> result = repository.getLabelCustomNetwork(1L, "non-existent-label-id", DunbarCircle.DUNBAR, 5, 10);

        assertThat(result).isEmpty();
    }

    // ───────── Two-Hop Edges ─────────

    @Test
    @DisplayName("2-Hop 엣지 조회 시 baseNetworkFriendIds에 포함된 실제 공통 친구 엣지를 반환한다")
    void getTwoHopContactEdgesForTarget_baseNetworkFriendIds_내_공통_친구_엣지를_반환한다() {
        // me(1)의 친구 중 targetX(100)와도 연결된: B(20), C(30)
        List<MutualFriendEdgeResult> result =
                repository.getTwoHopContactEdgesForTarget(1L, 100L, List.of(10L, 20L, 30L, 40L, 50L, 60L), 5);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MutualFriendEdgeResult::friendAId)
                .containsOnly(100L);
        assertThat(result).extracting(MutualFriendEdgeResult::friendBId)
                .containsExactlyInAnyOrder(20L, 30L);
        assertThat(result).allMatch(r -> r.intimacy() == null);
    }

    @Test
    @DisplayName("2-Hop 엣지 조회 시 baseNetworkFriendIds에 없는 친구는 결과에 포함되지 않는다")
    void getTwoHopContactEdgesForTarget_baseNetworkFriendIds_밖의_친구는_결과에_포함되지_않는다() {
        // B(20), C(30)를 baseNetworkFriendIds에서 제외
        List<MutualFriendEdgeResult> result =
                repository.getTwoHopContactEdgesForTarget(1L, 100L, List.of(10L, 40L, 50L, 60L), 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("2-Hop 접점 조회 시 실제 내 친구가 아닌 ID는 HAS_FRIENDSHIP 검증에서 제외된다")
    void getTwoHopContactEdgesForTarget_비친구_ID는_보안_검증에서_제외된다() {
        // 999, 998은 me의 친구가 아님
        List<MutualFriendEdgeResult> result =
                repository.getTwoHopContactEdgesForTarget(1L, 100L, List.of(999L, 998L), 5);

        assertThat(result).isEmpty();
    }

    // ───────── One-Hop Edges ─────────

    @Test
    @DisplayName("1-Hop 엣지 조회 시 skeletonIds 내 공통 친구 엣지를 친밀도 내림차순으로 반환한다")
    void getDirectFriendEdgesForTarget_baseNetworkFriendIds_내_공통_친구_엣지를_반환한다() {
        // targetX(100)와 연결된 me 친구: B(20, intimacy=0.6), C(30, intimacy=0.4)
        List<MutualFriendEdgeResult> result =
                repository.getDirectFriendEdgesForTarget(1L, 100L, List.of(10L, 20L, 30L, 40L, 50L, 60L), 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).friendBId()).isEqualTo(20L);  // 높은 친밀도 먼저
        assertThat(result.get(1).friendBId()).isEqualTo(30L);
        assertThat(result).allMatch(e -> e.friendAId() == 100L);
    }

    @Test
    @DisplayName("1-Hop 엣지 조회 시 dynamicLimit만큼만 반환된다")
    void getDirectFriendEdgesForTarget_dynamicLimit만큼만_반환된다() {
        // B(0.6), C(0.4) 중 limit=1이면 친밀도 높은 B만 반환
        List<MutualFriendEdgeResult> result =
                repository.getDirectFriendEdgesForTarget(1L, 100L, List.of(10L, 20L, 30L, 40L, 50L, 60L), 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).friendBId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("1-Hop 엣지 조회 시 실제 내 친구가 아닌 ID는 HAS_FRIENDSHIP 검증에서 제외된다")
    void getDirectFriendEdgesForTarget_비친구_ID는_보안_검증에서_제외된다() {
        List<MutualFriendEdgeResult> result =
                repository.getDirectFriendEdgesForTarget(1L, 100L, List.of(999L, 998L), 10);

        assertThat(result).isEmpty();
    }
}

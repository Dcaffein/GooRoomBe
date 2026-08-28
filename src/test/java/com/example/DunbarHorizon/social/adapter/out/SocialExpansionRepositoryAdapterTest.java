package com.example.DunbarHorizon.social.adapter.out;

import com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.SocialExpansionRepositoryAdapter;
import com.example.DunbarHorizon.social.application.dto.result.AnchorExpansionResult;
import com.example.DunbarHorizon.support.Neo4jRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Neo4jRepositoryTest
@Import(SocialExpansionRepositoryAdapter.class)
class SocialExpansionRepositoryAdapterTest {

    @Autowired
    private SocialExpansionRepositoryAdapter expansionRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setupGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        /*
         * 테스트 시나리오 구성
         * - 나(1), 앵커(10), 내친구A(2)
         * - 앵커의 타겟들:
         * 1. 타겟1(100): 나와 친구 → Related 모드에서만 포함
         * 2. 타겟2(200): 나와 친구 아님, 고관여 (공통친구 내친구A, 공유 라벨 → 점수 2) → Recommended 모드에서만 포함
         * 3. 타겟3(300): 나와 친구 아님, 저관여 (공통친구 0, 공유 라벨 0 → 점수 0) → Recommended 모드에서만 포함
         * 4. 타겟4(400): 비공개 타겟 (isRoutable: false → 어느 모드에서도 조회 불가)
         */
        neo4jClient.query("""
            CREATE (me:UserReference {id: 1, nickname: '나'})
            CREATE (anchor:UserReference {id: 10, nickname: '앵커'})
            CREATE (fa:UserReference {id: 2, nickname: '내친구A'})
            
            CREATE (t1:UserReference {id: 100, nickname: '이미친구타겟'})
            CREATE (t2:UserReference {id: 200, nickname: '추천타겟_고관여'})
            CREATE (t3:UserReference {id: 300, nickname: '추천타겟_저관여'})
            CREATE (t4:UserReference {id: 400, nickname: '비공개타겟'})
            
            CREATE (label1:Label {name: '공통관심사'})
            
            // 1. 1촌 관계 형성: (User)-[:HAS_FRIENDSHIP]->(Friendship)<-[:HAS_FRIENDSHIP]-(User)
            CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.9})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(anchor)
            CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.8})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(fa)
            CREATE (me)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.7})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(t1)
            
            // 2. 앵커(Anchor)의 2-Hop 타겟 관계 설정 
            CREATE (anchor)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.9})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(t1)
            CREATE (anchor)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.8})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(t2)
            CREATE (anchor)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.7})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(t3)
            // 타겟4에서 출발하는 엣지에 isRoutable: false 설정
            CREATE (anchor)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.6})<-[:HAS_FRIENDSHIP {isRoutable: false}]-(t4)
            
            // 3. 공통 친구 형성 (타겟2와 내친구A가 서로 친구) -> 이로써 타겟2는 공통친구 1명 확보
            CREATE (t2)-[:HAS_FRIENDSHIP {isRoutable: true}]->(:Friendship {intimacy: 0.5})<-[:HAS_FRIENDSHIP {isRoutable: true}]-(fa)
            
            // 4. 공유 라벨 형성
            // 쿼리 패턴: ownsLabel(anchor, label).relationshipTo(target, ATTACHED_TO)
            CREATE (anchor)-[:OWNS_LABEL]->(label1)
                      
            // 생성된 하나의 라벨에서 각 타겟으로 뻗어나가는 연결 관계 생성
            CREATE (label1)-[:ATTACHED_TO]->(me)
            CREATE (label1)-[:ATTACHED_TO]->(t1)
            CREATE (label1)-[:ATTACHED_TO]->(t2)
        """).run();
    }

    @Test
    @DisplayName("Related 모드: 앵커의 친구 중 나와도 친구인 사람만 조회한다")
    void getRelatedNetworkByAnchor_Success() {
        List<AnchorExpansionResult> result = expansionRepository.getRelatedNetworkByAnchor(1L, 10L, 0, 10);

        // 앵커 친구 중 나(1)와도 친구인 t1(100)만 포함, 내 친구 아닌 t2/t3는 제외, t4는 isRoutable=false라 제외
        assertThat(result).hasSize(1);
        assertThat(result).extracting(AnchorExpansionResult::id)
                .containsExactlyInAnyOrder(100L);
    }

    @Test
    @DisplayName("Recommended 모드: 앵커의 친구 중 나와 친구가 아닌 사람만 조회한다")
    void getRecommendedNetworkByAnchor_OnlyStrangers() {
        List<AnchorExpansionResult> result = expansionRepository.getRecommendedNetworkByAnchor(1L, 10L, 0, 10);

        // 나와 친구인 t1(100)은 제외, 친구 아닌 t2(200)·t3(300)만 포함
        assertThat(result).hasSize(2);
        assertThat(result).extracting(AnchorExpansionResult::id)
                .containsExactlyInAnyOrder(200L, 300L);
    }

    @Test
    @DisplayName("공통 친구 수와 공유 라벨 수의 합산이 threshold 이상인 데이터만 정확히 계산되어 반환된다")
    void executeAnchorExpansionQuery_ThresholdFilter() {
        // 타겟2(200): 공통친구 1 + 공유라벨 1 = 총점 2점
        // 타겟3(300): 공통친구 0 + 공유라벨 0 = 총점 0점

        // threshold가 2점일 때 (타겟2만 통과)
        List<AnchorExpansionResult> result2 = expansionRepository.getRecommendedNetworkByAnchor(1L, 10L, 2, 10);
        assertThat(result2).hasSize(1);
        assertThat(result2.get(0).id()).isEqualTo(200L);

        // threshold가 3점일 때 (아무도 통과 못함)
        List<AnchorExpansionResult> result3 = expansionRepository.getRecommendedNetworkByAnchor(1L, 10L, 3, 10);
        assertThat(result3).isEmpty();
    }

    @Test
    @DisplayName("타겟이 앵커에게 노출 권한을 끄면(isRoutable=false) 어떠한 경우에도 조회되지 않는다")
    void executeAnchorExpansionQuery_PrivacyFilter() {
        List<AnchorExpansionResult> result = expansionRepository.getRelatedNetworkByAnchor(1L, 10L, 0, 10);

        // t4(400)은 노출 차단 상태이므로 포함되지 않아야 함
        assertThat(result).extracting(AnchorExpansionResult::id).doesNotContain(400L);
    }
}

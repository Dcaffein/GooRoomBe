package com.example.DunbarHorizon.social.adapter.out.persistence.neo4j;

import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.application.port.out.SocialNetworkRepository;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.schema.SocialGraphSchema.*;
import static com.example.DunbarHorizon.social.domain.friend.constant.FriendConstants.*;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.ATTACHED_TO;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.LABEL;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.OWNS_LABEL;
import static com.example.DunbarHorizon.social.domain.socialUser.constant.SocialUserConstants.USER_REFERENCE;

@Repository
@RequiredArgsConstructor
public class SocialNetworkRepositoryAdapter implements SocialNetworkRepository {

    private final Neo4jClient neo4jClient;

    // GET_DEFAULT_NETWORK_GRAPH, GET_LABEL_CUSTOM_NETWORK 공통 suffix
    private static final String NETWORK_PRUNING_SUFFIX = ("""
            // [1] 친구 목록 수집 + interestScore lookup map 구성
            WITH me,
                 collect({member: member, friendship: myFriendship, interestScore: interestScore}) AS friendData
            WITH me, friendData,
                 apoc.coll.union([x IN friendData | x.member], [me]) AS boundary,
                 apoc.map.fromPairs([x IN friendData | [toString(x.member.#{ID}), x.interestScore]]) AS interestMap

            // [2] 친구별 동적 엣지 한도 산출 (me→friend 친밀도가 높을수록 더 많은 내부 엣지 허용)
            UNWIND friendData AS item
            WITH me, boundary, interestMap,
                 item.member AS member,
                 toInteger($pruningMin + coalesce(item.friendship.#{INTIMACY}, 0.0) * $pruningRange) AS dynamicLimit

            // [3] boundary 내에서 각 친구의 내부 엣지를 dynamicLimit만큼 슬라이싱 (ID만 수집)
            // OPTIONAL MATCH: 연결이 없는 고립 노드도 1 row(null)로 통과시켜 [4]에서 빈 리스트로 반환
            CALL (me, member, boundary, dynamicLimit) {
              OPTIONAL MATCH (member)-[:#{HF}]->(innerFriendship:#{F})<-[:#{HF}]-(targetMember:#{UR})
              WHERE targetMember IN boundary AND member <> targetMember AND targetMember <> me
              WITH innerFriendship, targetMember, dynamicLimit
              ORDER BY innerFriendship.#{INTIMACY} DESC
              WITH collect(
                CASE WHEN targetMember IS NOT NULL
                THEN {intimacy: innerFriendship.#{INTIMACY}, targetMemberId: targetMember.#{ID}}
                ELSE null END
              ) AS allEdges, dynamicLimit
              RETURN allEdges[0..dynamicLimit] AS topEdges
            }

            // [4] 친구별 nodeId + 엣지 목록 반환 (고립 노드도 빈 리스트로 포함)
            RETURN member.#{ID} AS nodeId,
                   interestMap[toString(member.#{ID})] AS nodeInterest,
                   [e IN topEdges | {
                     friendId:       e.targetMemberId,
                     intimacy:       e.intimacy,
                     friendInterest: interestMap[toString(e.targetMemberId)]
                   }] AS memberEdges
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{INTIMACY}", PROP_INTIMACY);

    private static final String GET_DEFAULT_NETWORK_GRAPH =
            ("""
            // [0] me의 친구를 친밀도 내림차순으로 circleSize만큼 선정
            MATCH (me:#{UR} {#{ID}: $meId})-[r_me:#{HF}]->(myFriendship:#{F})<-[:#{HF}]-(member:#{UR})
            WITH me, member, myFriendship, coalesce(r_me.#{INTEREST}, 0.0) AS interestScore
            ORDER BY myFriendship.#{INTIMACY} DESC
            LIMIT $limitSize
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{INTIMACY}", PROP_INTIMACY)
            .replace("#{INTEREST}", PROP_INTEREST_SCORE)
            + NETWORK_PRUNING_SUFFIX;

    private static final String GET_LABEL_CUSTOM_NETWORK =
            ("""
            // [0] 지정 라벨에 속한 친구만 네트워크 풀로 제한
            MATCH (me:#{UR} {#{ID}: $meId})
            MATCH (me)-[:#{OL}]->(label:#{LBL})-[:#{AT}]->(member:#{UR})
            WHERE label.#{ID} = $labelId
            MATCH (me)-[r_me:#{HF}]->(myFriendship:#{F})<-[:#{HF}]-(member)
            WITH me, member, myFriendship, coalesce(r_me.#{INTEREST}, 0.0) AS interestScore
            ORDER BY myFriendship.#{INTIMACY} DESC
            LIMIT $limitSize
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{LBL}", LABEL)
            .replace("#{OL}", OWNS_LABEL)
            .replace("#{AT}", ATTACHED_TO)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{INTIMACY}", PROP_INTIMACY)
            .replace("#{INTEREST}", PROP_INTEREST_SCORE)
            + NETWORK_PRUNING_SUFFIX;

    private static final String GET_TWO_HOP_CONTACT_EDGES_FOR_TARGET = ("""
            // 2-hop target과 현재 화면 skeleton 내에서 공통 친구를 친밀도 순으로 최대 5명 반환
            // baseNetworkFriendIds: 클라이언트가 전달한 기준 네트워크의 친구 ID 목록 (보안 검증 겸용)
            MATCH (me:#{UR} {#{ID}: $meId})
            WITH me
            MATCH (target:#{UR} {#{ID}: $targetId})
            CALL (me, target) {
              MATCH (me)-[:#{HF}]->(:#{F})<-[:#{HF}]-(mutual:#{UR})
              WHERE mutual.#{ID} IN $baseNetworkFriendIds
              MATCH (target)-[:#{HF}]->(tf:#{F})<-[:#{HF}]-(mutual)
              ORDER BY tf.#{INTIMACY} DESC
              LIMIT $strangerQuota
              RETURN mutual, tf
            }
            RETURN target.#{ID} AS friendAId, mutual.#{ID} AS friendBId
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{INTIMACY}", PROP_INTIMACY);

    private static final String GET_DIRECT_FRIEND_EDGES_FOR_TARGET = ("""
            // 동적으로 새 노드를 추가할 때 기존 네트워크와의 연결 엣지를 반환
            // dynamicLimit: me→target 친밀도 기반으로 서비스 레이어에서 계산 (5 + intimacy * 5)
            // baseNetworkFriendIds: 기준 네트워크의 친구 ID (내 친구인지 검증 + 공통 친구 필터)
            MATCH (me:#{UR} {#{ID}: $meId})
            WITH me
            MATCH (target:#{UR} {#{ID}: $targetId})
            CALL (me, target) {
              MATCH (me)-[:#{HF}]->(:#{F})<-[:#{HF}]-(mutual:#{UR})
              WHERE mutual.#{ID} IN $baseNetworkFriendIds
              MATCH (target)-[:#{HF}]->(tf:#{F})<-[:#{HF}]-(mutual)
              ORDER BY tf.#{INTIMACY} DESC
              LIMIT $dynamicLimit
              RETURN mutual, tf
            }
            RETURN target.#{ID} AS friendAId, mutual.#{ID} AS friendBId, tf.#{INTIMACY} AS intimacy
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{INTIMACY}", PROP_INTIMACY);

    @Cacheable(cacheNames = "dunbar:network:default", key = "#userId + ':' + #circleSize.name()")
    @Neo4jTransactional(readOnly = true)
    @Override
    public List<NodeGraphResult> getDefaultNetworkGraph(Long userId, DunbarCircle circleSize, int pruningMin, int pruningRange) {
        return neo4jClient.query(GET_DEFAULT_NETWORK_GRAPH)
                .bind(userId).to("meId")
                .bind(circleSize.getLimitSize()).to("limitSize")
                .bind(pruningMin).to("pruningMin")
                .bind(pruningRange).to("pruningRange")
                .fetchAs(NodeGraphResult.class)
                .mappedBy((ts, r) -> mapNodeGraphResult(r))
                .all().stream().toList();
    }

    @Cacheable(cacheNames = "dunbar:network:label", key = "#userId + ':' + #labelId")
    @Neo4jTransactional(readOnly = true)
    @Override
    public List<NodeGraphResult> getLabelCustomNetwork(Long userId, String labelId, DunbarCircle circleSize, int pruningMin, int pruningRange) {
        return neo4jClient.query(GET_LABEL_CUSTOM_NETWORK)
                .bind(userId).to("meId")
                .bind(labelId).to("labelId")
                .bind(circleSize.getLimitSize()).to("limitSize")
                .bind(pruningMin).to("pruningMin")
                .bind(pruningRange).to("pruningRange")
                .fetchAs(NodeGraphResult.class)
                .mappedBy((ts, r) -> mapNodeGraphResult(r))
                .all().stream().toList();
    }

    private static NodeGraphResult mapNodeGraphResult(org.neo4j.driver.Record record) {
        Long nodeId = record.get("nodeId").asLong();
        double nodeInterest = record.get("nodeInterest").asDouble(0.0);
        List<NodeEdgeResult> edges = record.get("memberEdges").asList(e ->
                new NodeEdgeResult(
                        e.get("friendId").asLong(),
                        e.get("intimacy").asDouble(0.0),
                        e.get("friendInterest").asDouble(0.0)
                )
        );
        return new NodeGraphResult(nodeId, nodeInterest, edges);
    }

    @Override
    public List<MutualFriendEdgeResult> getTwoHopContactEdgesForTarget(
            Long userId, Long targetId, List<Long> baseNetworkFriendIds, int strangerQuota) {
        return neo4jClient.query(GET_TWO_HOP_CONTACT_EDGES_FOR_TARGET)
                .bind(userId).to("meId")
                .bind(targetId).to("targetId")
                .bind(baseNetworkFriendIds).to("baseNetworkFriendIds")
                .bind(strangerQuota).to("strangerQuota")
                .fetchAs(MutualFriendEdgeResult.class)
                .mappedBy((typeSystem, record) -> new MutualFriendEdgeResult(
                        record.get("friendAId").asLong(),
                        record.get("friendBId").asLong(),
                        null
                ))
                .all().stream().toList();
    }

    @Override
    public List<MutualFriendEdgeResult> getDirectFriendEdgesForTarget(
            Long userId, Long targetId, List<Long> baseNetworkFriendIds, int dynamicLimit) {
        return neo4jClient.query(GET_DIRECT_FRIEND_EDGES_FOR_TARGET)
                .bind(userId).to("meId")
                .bind(targetId).to("targetId")
                .bind(baseNetworkFriendIds).to("baseNetworkFriendIds")
                .bind(dynamicLimit).to("dynamicLimit")
                .fetchAs(MutualFriendEdgeResult.class)
                .mappedBy((typeSystem, record) -> new MutualFriendEdgeResult(
                        record.get("friendAId").asLong(),
                        record.get("friendBId").asLong(),
                        record.get("intimacy").asDouble()
                ))
                .all().stream().toList();
    }
}

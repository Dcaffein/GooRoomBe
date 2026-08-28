package com.example.DunbarHorizon.social.adapter.out.persistence.neo4j;

import com.example.DunbarHorizon.social.application.dto.result.AnchorExpansionResult;
import com.example.DunbarHorizon.social.application.port.out.SocialExpansionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.schema.SocialGraphSchema.*;
import static com.example.DunbarHorizon.social.domain.friend.constant.FriendConstants.FRIENDSHIP;
import static com.example.DunbarHorizon.social.domain.friend.constant.FriendConstants.HAS_FRIENDSHIP;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.ATTACHED_TO;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.LABEL;
import static com.example.DunbarHorizon.social.domain.label.constant.LabelConstants.OWNS_LABEL;
import static com.example.DunbarHorizon.social.domain.socialUser.constant.SocialUserConstants.USER_REFERENCE;

@Repository
@RequiredArgsConstructor
public class SocialExpansionRepositoryAdapter implements SocialExpansionRepository {

    private final Neo4jClient neo4jClient;

    private static final String ANCHOR_EXPANSION_QUERY = ("""
            // [1] me와 anchor 확인, me의 1-hop 친구 목록 수집 (anchor 제외)
            MATCH (me:#{UR} {#{ID}: $meId})
            MATCH (anchor:#{UR} {#{ID}: $anchorId})
            MATCH (me)-[:#{HF}]->(:#{F})<-[:#{HF}]-(myFriendNode:#{UR})
            WHERE myFriendNode.#{ID} <> anchor.#{ID}
            WITH me, anchor, collect(distinct myFriendNode) AS myFriends

            // [2] anchor가 소유하고 me가 속한 라벨 수집 (맥락 유사도 기준)
            OPTIONAL MATCH (anchor)-[:#{OL}]->(sharedLabel:#{LBL})-[:#{AT}]->(me)
            WITH me, anchor, myFriends, collect(distinct sharedLabel) AS mySharedLabels

            // [3] anchor의 2-hop 후보 탐색, isRoutable 필터 + 친구 범위 조건 적용 후 친밀도 내림차순 정렬
            MATCH (anchor)-[:#{HF}]->(targetFriendship:#{F})<-[targetRel:#{HF}]-(target:#{UR})
            WHERE target.#{ID} <> me.#{ID}
              AND targetRel.#{IR} = true
              AND ($onlyMyFriends = (target IN myFriends))
            WITH target, targetFriendship, me, anchor, myFriends, mySharedLabels
            ORDER BY targetFriendship.#{INTIMACY} DESC

            // [4] 공통 지인 수 산출 (신뢰도 측정)
            CALL (target, myFriends, me, anchor) {
              MATCH (target)-[:#{HF}]->(:#{F})<-[:#{HF}]-(mutual:#{UR})
              WHERE mutual IN myFriends
                AND mutual.#{ID} <> me.#{ID}
                AND mutual.#{ID} <> anchor.#{ID}
              RETURN count(distinct mutual) AS mutualCount
            }

            // [5] 공유 라벨 수 산출 (맥락 유사도 가중치)
            WITH target, targetFriendship, mutualCount, mySharedLabels, anchor
            WITH target, targetFriendship, mutualCount,
                 size([(anchor)-[:#{OL}]->(tl:#{LBL})-[:#{AT}]->(target) WHERE tl IN mySharedLabels | tl]) AS labelCount

            // [6] threshold 이상인 후보만 반환
            WHERE mutualCount + labelCount >= $threshold
            RETURN target.#{ID}       AS id,
                   target.#{NICK}     AS nickname
            LIMIT $limitCount
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{LBL}", LABEL)
            .replace("#{OL}", OWNS_LABEL)
            .replace("#{AT}", ATTACHED_TO)
            .replace("#{ID}", PROP_ID)
            .replace("#{NICK}", PROP_NICKNAME)
            .replace("#{INTIMACY}", PROP_INTIMACY)
            .replace("#{IR}", PROP_IS_ROUTABLE);

    @Override
    public List<AnchorExpansionResult> getRelatedNetworkByAnchor(Long meId, Long anchorId, int threshold, int limitCount) {
        return executeQuery(meId, anchorId, threshold, limitCount, true);
    }

    @Override
    public List<AnchorExpansionResult> getRecommendedNetworkByAnchor(Long meId, Long anchorId, int threshold, int limitCount) {
        return executeQuery(meId, anchorId, threshold, limitCount, false);
    }

    private List<AnchorExpansionResult> executeQuery(Long meId, Long anchorId, int threshold, int limitCount, boolean onlyMyFriends) {
        return neo4jClient.query(ANCHOR_EXPANSION_QUERY)
                .bind(meId).to("meId")
                .bind(anchorId).to("anchorId")
                .bind(threshold).to("threshold")
                .bind(limitCount).to("limitCount")
                .bind(onlyMyFriends).to("onlyMyFriends")
                .fetchAs(AnchorExpansionResult.class)
                .mappedBy((typeSystem, record) -> new AnchorExpansionResult(
                        record.get("id").asLong(),
                        record.get("nickname").asString()
                ))
                .all()
                .stream()
                .toList();
    }
}

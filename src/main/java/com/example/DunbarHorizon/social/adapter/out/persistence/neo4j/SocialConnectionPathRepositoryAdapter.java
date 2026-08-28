package com.example.DunbarHorizon.social.adapter.out.persistence.neo4j;

import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;
import com.example.DunbarHorizon.social.application.port.out.SocialConnectionPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.schema.SocialGraphSchema.*;
import static com.example.DunbarHorizon.social.domain.friend.constant.FriendConstants.FRIENDSHIP;
import static com.example.DunbarHorizon.social.domain.friend.constant.FriendConstants.HAS_FRIENDSHIP;
import static com.example.DunbarHorizon.social.domain.socialUser.constant.SocialUserConstants.USER_REFERENCE;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialConnectionPathRepositoryAdapter implements SocialConnectionPathRepository {

    private final Neo4jClient neo4jClient;

    // score는 정렬 기준으로만 쓰고 반환하지 않는다. 남 두 사람의 친밀도를 제3자에게 주지 않기 위함
    // collect 앞의 ORDER BY가 리스트 순서를 만들고, size()는 자르기 전에 세어 전체 수를 낸다
    private static final String INTERMEDIARIES_QUERY = ("""
            MATCH (me:#{UR} {#{ID}: $myId})-[:#{HF}]->(f1:#{F})<-[r2:#{HF}]-(mid:#{UR})
                  -[r3:#{HF}]->(f2:#{F})<-[r4:#{HF}]-(target:#{UR} {#{ID}: $targetId})
            WHERE r2.#{IR} = true AND r4.#{IR} = true
            WITH mid, sqrt(f1.#{INTIMACY} * f2.#{INTIMACY}) AS score
            ORDER BY score DESC
            WITH collect({userId: mid.#{ID}, nickname: mid.#{NICK}}) AS ranked
            RETURN ranked[0..$limit] AS intermediaries, size(ranked) AS totalCount
            """)
            .replace("#{UR}", USER_REFERENCE)
            .replace("#{F}", FRIENDSHIP)
            .replace("#{HF}", HAS_FRIENDSHIP)
            .replace("#{ID}", PROP_ID)
            .replace("#{NICK}", PROP_NICKNAME)
            .replace("#{INTIMACY}", PROP_INTIMACY)
            .replace("#{IR}", PROP_IS_ROUTABLE);

    @Override
    public ConnectionPathResult.Intermediaries findIntermediaries(Long myId, Long targetId, int limit) {
        return neo4jClient.query(INTERMEDIARIES_QUERY)
                .bind(myId).to("myId")
                .bind(targetId).to("targetId")
                .bind(limit).to("limit")
                .fetchAs(ConnectionPathResult.Intermediaries.class)
                .mappedBy((typeSystem, record) -> new ConnectionPathResult.Intermediaries(
                        record.get("intermediaries").asList(value ->
                                new ConnectionPathResult.IntermediaryResult(
                                        value.get("userId").asLong(),
                                        value.get("nickname").asString()
                                )),
                        record.get("totalCount").asInt()
                ))
                .one()
                .orElseGet(() -> new ConnectionPathResult.Intermediaries(List.of(), 0));
    }
}

package com.example.GooRoomBe.social.query;

import com.example.GooRoomBe.social.query.api.FriendSuggestionDto;
import com.example.GooRoomBe.social.query.api.OneHopsNetworkDto;
import lombok.RequiredArgsConstructor;
import org.neo4j.cypherdsl.core.*;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.HAS_MEMBER;
import static com.example.GooRoomBe.social.common.SocialSchemaConstants.OWNS;
import static com.example.GooRoomBe.social.query.Neo4jVariables.ME;
import static com.example.GooRoomBe.social.query.Neo4jVariables.ONE_HOP_FRIEND;
import static org.neo4j.cypherdsl.core.Cypher.*;

import static com.example.GooRoomBe.social.query.Neo4jVariables.*;
import static com.example.GooRoomBe.social.query.Neo4jConditions.*;
import static com.example.GooRoomBe.social.query.Neo4jPatterns.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialQueryService {

    private final Neo4jTemplate neo4jTemplate;

    /**
     * 현재 사용자의 친구 목록과 함께 아는 친구 정보를 조회
     */
    public List<OneHopsNetworkDto> getOneHopsNetwork(String userId) {

        //  1-hop 패턴 호출
        StatementBuilder.OngoingReadingAndWith firstWith = findOneHopFriends(literalOf(userId))
                .with(ME, ONE_HOP_FRIEND, ALIAS);

        // 2-hop(MUTUAL_FRIEND) 패턴 호출
        StatementBuilder.OngoingReadingAndWith secondWith = findTwoHopPatternOptional(firstWith, MUTUAL_FRIEND)
                .and(isFriends(ME, MUTUAL_FRIEND)) // 함께 아는 친구 조건
                .with(ME, ONE_HOP_FRIEND, ALIAS, MUTUAL_FRIEND);

        Statement statement = secondWith
                .with(
                        ONE_HOP_FRIEND,
                        ALIAS,
                        collectDistinct(MUTUAL_FRIEND.property("id")).as("mutualFriendIds")
                )
                .returning(
                        ONE_HOP_FRIEND.property("id").as("friendId"),
                        ONE_HOP_FRIEND.property("nickname").as("friendName"),
                        ALIAS,
                        name("mutualFriendIds")
                )
                .build();

        return neo4jTemplate.findAll(statement, OneHopsNetworkDto.class);
    }

    /**
     * 2-hop 친구
     * 1-hop에 대한 onIntroduce : true
     * 1-hop 친구가 가진 라벨 중 내가 멤버인 라벨의 다른 멤버들을 2 hop 으로 소개
     */
    public List<FriendSuggestionDto> getTwoHopSuggestion(String userId) {

        Property myRelOnIntroduce = MY_REL.property("onIntroduce");
        Node labelNamed = LABEL_NAMED;

        // 1-hop 친구 찾기
        var firstWith = findOneHopFriends(literalOf(userId))
                .and(myRelOnIntroduce.isTrue())
                .with(ME, ONE_HOP_FRIEND);

        //  라벨 매칭 조건 강화
        var secondWith = firstWith
                // 친구가 소유한 라벨
                .match(labelNamed.relationshipFrom(ONE_HOP_FRIEND, OWNS))
                .where(labelNamed.property(PROP_EXPOSURE).isTrue())
                // 그 라벨에 me 포함
                .match(labelNamed.relationshipTo(ME, HAS_MEMBER))
                // 그 라벨의 다른 멤버(Target) 찾기
                .match(labelNamed.relationshipTo(TWO_HOP_FRIEND, HAS_MEMBER))

                .with(ME, ONE_HOP_FRIEND, TWO_HOP_FRIEND);

        Statement statement = secondWith
                .where(TWO_HOP_FRIEND.property("id").isNotEqualTo(ME.property("id")))
                .and(TWO_HOP_FRIEND.property("id").isNotEqualTo(ONE_HOP_FRIEND.property("id")))
                .and(isNotFriends(ME, TWO_HOP_FRIEND))
                .returningDistinct(
                        TWO_HOP_FRIEND.property("id").as("suggestedFriendId"),
                        TWO_HOP_FRIEND.property("nickname").as("suggestedFriendName"),
                        ONE_HOP_FRIEND.property("id").as("commonFriendId")
                )
                .build();

        return neo4jTemplate.findAll(statement, FriendSuggestionDto.class);
    }
}
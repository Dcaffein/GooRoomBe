package com.example.GooRoomBe.social.query;

import org.neo4j.cypherdsl.core.*;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.MEMBER_OF;
import static com.example.GooRoomBe.social.query.Neo4jVariables.*;

public final class Neo4jPatterns {

    public static final Relationship MY_REL = ME.relationshipTo(FRIEND_SHIP_NODE, MEMBER_OF).named("myRel");
    public static final Relationship ONE_HOP_REL = ONE_HOP_FRIEND.relationshipTo(FRIEND_SHIP_NODE, MEMBER_OF).named("oneHopRel");
    public static final AliasedExpression ALIAS = MY_REL.property("friendAlias").as("friendAlias");

    /**
     * 1-HOP 친구 찾기
     * MATCH (me:User {id: $userId})<-[myRel]-(FS)-...->(friend1:User)
     * WHERE friend1.id <> me.id
     * @param userIdParam Cypher 파라미터 (e.g., literalOf(userId))
     * @return .and() 또는 .with()를 호출할 수 있는 'OngoingReadingWithWhere' 빌더
     */
    public static StatementBuilder.OngoingReadingWithWhere findOneHopFriends(Expression userIdParam) {

        return Cypher.match(
                        MY_REL, // (me)-[myRel]->(fs)
                        FRIEND_SHIP_NODE.relationshipFrom(ONE_HOP_FRIEND, MEMBER_OF) // (fs) <- (oneHopFriend)
                )
                .where(ME.property("id").isEqualTo(userIdParam))
                .and(ONE_HOP_FRIEND.property("id").isNotEqualTo(ME.property("id")));
    }

    /**
     * 2-HOP 친구 찾
     * MATCH (friend1)<-[friend1Rel]-(FS)-...->(targetNode:User)
     * WHERE targetNode.id <> me.id AND targetNode.id <> friend1.id
     * @param previousBuilder .with()로 1-hop 단계를 마친 빌더
     * @param targetNode 2-hop 친구를 바인딩할 노드 변수 (e.g., FRIEND_OF_FRIEND or MUTUAL_FRIEND)
     * @return .and() 또는 .with()를 호출할 수 있는 OngoingReadingWithWhere
     */
    public static StatementBuilder.OngoingReadingWithWhere findTwoHopPatternOptional(
            StatementBuilder.OngoingReadingAndWith previousBuilder,
            Node targetNode
    ) {
        // ⭐️ match -> optionalMatch 로 변경
        return previousBuilder.optionalMatch(
                        ONE_HOP_REL,
                        FRIEND_SHIP_NODE.relationshipFrom(targetNode, MEMBER_OF)
                )
                .where(targetNode.property("id").isNotEqualTo(ME.property("id")))
                .and(targetNode.property("id").isNotEqualTo(ONE_HOP_FRIEND.property("id")));
    }

    private Neo4jPatterns() {}
}
package com.example.GooRoomBe.social.query;

import org.neo4j.cypherdsl.core.Condition;
import org.neo4j.cypherdsl.core.Node;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.MEMBER_OF;
import static org.neo4j.cypherdsl.core.Cypher.exists;
import static org.neo4j.cypherdsl.core.Cypher.not;
import static com.example.GooRoomBe.social.query.Neo4jVariables.*;

public final class Neo4jConditions {

    /**
     * 두 사용자가 친구 관계인지 확인하는 조건을 반환
     * @param nodeA 사용자 1
     * @param nodeB 사용자 2
     * @return 'EXISTS (nodeA)-->(FRIEND_SHIP)-->(nodeB)' 조건
     */
    public static Condition isFriends(Node nodeA, Node nodeB) {
        // (nodeA)-[MEMBER_OF]->(FriendShip)<-[MEMBER_OF]-(nodeB)
        return exists(
                nodeA.relationshipTo(FRIEND_SHIP, MEMBER_OF)
                        .relationshipFrom(nodeB, MEMBER_OF)
        );
    }

    /**
     * 두 사용자가 친구 관계가 *아닌지* 확인하는 조건을 반환합니다.
     * @param nodeA 사용자 1
     * @param nodeB 사용자 2
     * @return 'NOT EXISTS (nodeA)-->(FRIEND_SHIP)-->(nodeB)' 조건
     */
    public static Condition isNotFriends(Node nodeA, Node nodeB) {
        return not(isFriends(nodeA, nodeB));
    }

    private Neo4jConditions() {}
}
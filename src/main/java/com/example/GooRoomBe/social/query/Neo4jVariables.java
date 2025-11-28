package com.example.GooRoomBe.social.query;

import com.example.GooRoomBe.social.common.SocialSchemaConstants;
import org.neo4j.cypherdsl.core.Node;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;
import static org.neo4j.cypherdsl.core.Cypher.node;

public final class Neo4jVariables {

    // --- 1. 기본 노드 스키마 정의 ---
    public static final Node USER = node(SOCIAL_USER);
    public static final Node FRIEND_SHIP = node(FRIENDSHIP);
    public static final Node LABEL = node(SocialSchemaConstants.LABEL);

    // --- 2. 쿼리용 이름있는 노드 변수 ---
    public static final Node ME = USER.named("me");
    public static final Node ONE_HOP_FRIEND = USER.named("oneHopFriend");
    public static final Node TWO_HOP_FRIEND = USER.named("twoHopFriend");
    public static final Node LABEL_NAMED = LABEL.named("label");
    public static final Node MUTUAL_FRIEND = USER.named("mutualFriend");

    public static final Node FRIEND_SHIP_NODE = FRIEND_SHIP.named("fs");


    public static final String PROP_NICKNAME = "nickname";
    public static final String PROP_EXPOSURE = "exposure";
    public static final String PROP_PROFILE_IMAGE = "profileImage";

    private Neo4jVariables() {}
}
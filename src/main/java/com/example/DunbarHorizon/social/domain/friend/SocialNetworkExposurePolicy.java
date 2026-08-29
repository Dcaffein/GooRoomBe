package com.example.DunbarHorizon.social.domain.friend;

import org.springframework.stereotype.Component;

/**
 * Social Network 조회에서 클라이언트 응답으로 내려갈 연결 수를 결정한다.
 */
@Component
public class SocialNetworkExposurePolicy {

    private static final int DIRECT_FRIEND_EDGE_BASE_LIMIT = 5;
    private static final int DIRECT_FRIEND_EDGE_INTIMACY_MULTIPLIER = 5;
    private static final int TWO_HOP_CONTACT_EDGE_LIMIT = 5;

    public int directFriendEdgeLimit(double intimacy) {
        return (int) (DIRECT_FRIEND_EDGE_BASE_LIMIT
                + intimacy * DIRECT_FRIEND_EDGE_INTIMACY_MULTIPLIER);
    }

    public int twoHopContactEdgeLimit() {
        return TWO_HOP_CONTACT_EDGE_LIMIT;
    }
}

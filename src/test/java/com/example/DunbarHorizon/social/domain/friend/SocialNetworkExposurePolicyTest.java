package com.example.DunbarHorizon.social.domain.friend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialNetworkExposurePolicyTest {

    private final SocialNetworkExposurePolicy policy = new SocialNetworkExposurePolicy();

    @Test
    @DisplayName("직접 친구 엣지 limit은 친밀도에 비례해 증가한다")
    void directFriendEdgeLimit_친밀도에_비례해_증가한다() {
        // given

        // when
        int minimumLimit = policy.directFriendEdgeLimit(0.0);
        int middleLimit = policy.directFriendEdgeLimit(0.5);
        int maximumLimit = policy.directFriendEdgeLimit(1.0);

        // then
        assertThat(minimumLimit).isEqualTo(5);
        assertThat(middleLimit).isEqualTo(7);
        assertThat(maximumLimit).isEqualTo(10);
    }

    @Test
    @DisplayName("2-hop 접점은 개인정보 노출 limit만큼만 조회한다")
    void twoHopContactEdgeLimit_고정된_노출_상한을_반환한다() {
        // given

        // when
        int limit = policy.twoHopContactEdgeLimit();

        // then
        assertThat(limit).isEqualTo(5);
    }
}

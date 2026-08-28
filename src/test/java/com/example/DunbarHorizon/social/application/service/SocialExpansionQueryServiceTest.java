package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.dto.result.AnchorExpansionResult;
import com.example.DunbarHorizon.social.application.port.out.SocialExpansionRepository;
import com.example.DunbarHorizon.social.domain.friend.Friendship;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendshipNotFoundException;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialExpansionQueryServiceTest {

    @Mock
    private SocialExpansionRepository socialExpansionRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private Friendship friendship;

    @InjectMocks
    private SocialExpansionQueryService service;

    // ── getAnchorExpansion ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAnchorExpansion: expansionValue=0.0이면 limit=10, threshold=5로 호출된다")
    void getAnchorExpansion_expansionValue_최솟값_파라미터_검증() {
        // given
        Long userId = 1L, anchorId = 10L;
        given(socialExpansionRepository.getRelatedNetworkByAnchor(userId, anchorId, 5, 10))
                .willReturn(List.of());

        // when
        service.getAnchorExpansion(userId, anchorId, 0.0);

        // then
        verify(socialExpansionRepository).getRelatedNetworkByAnchor(userId, anchorId, 5, 10);
    }

    @Test
    @DisplayName("getAnchorExpansion: expansionValue=0.8(변곡점)이면 limit=50, threshold=2로 호출된다")
    void getAnchorExpansion_expansionValue_변곡점_파라미터_검증() {
        // given
        Long userId = 1L, anchorId = 10L;
        given(socialExpansionRepository.getRelatedNetworkByAnchor(userId, anchorId, 2, 50))
                .willReturn(List.of());

        // when
        service.getAnchorExpansion(userId, anchorId, 0.8);

        // then
        verify(socialExpansionRepository).getRelatedNetworkByAnchor(userId, anchorId, 2, 50);
    }

    @Test
    @DisplayName("getAnchorExpansion: expansionValue=1.0이면 limit=150, threshold=1로 호출된다")
    void getAnchorExpansion_expansionValue_최댓값_파라미터_검증() {
        // given
        Long userId = 1L, anchorId = 10L;
        given(socialExpansionRepository.getRelatedNetworkByAnchor(userId, anchorId, 1, 150))
                .willReturn(List.of());

        // when
        service.getAnchorExpansion(userId, anchorId, 1.0);

        // then
        verify(socialExpansionRepository).getRelatedNetworkByAnchor(userId, anchorId, 1, 150);
    }

    @Test
    @DisplayName("getAnchorExpansion: 범위를 벗어난 expansionValue는 예외를 던진다")
    void getAnchorExpansion_잘못된_expansionValue_예외() {
        // when & then
        assertThatThrownBy(() -> service.getAnchorExpansion(1L, 10L, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getAnchorExpansion(1L, 10L, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getAnchorExpansion(1L, 10L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAnchorExpansion: Repository 결과를 그대로 반환한다")
    void getAnchorExpansion_결과_반환() {
        // given
        Long userId = 1L, anchorId = 10L;
        List<AnchorExpansionResult> expected = List.of(
                new AnchorExpansionResult(100L, "유저A")
        );
        given(socialExpansionRepository.getRelatedNetworkByAnchor(eq(userId), eq(anchorId), anyInt(), anyInt()))
                .willReturn(expected);

        // when
        List<AnchorExpansionResult> result = service.getAnchorExpansion(userId, anchorId, 0.5);

        // then
        assertThat(result).isEqualTo(expected);
    }

    // ── getRecommendationsByAnchor ────────────────────────────────────────────

    @Test
    @DisplayName("getRecommendationsByAnchor: Friendship.intimacy를 기반으로 limit/threshold를 계산해 호출된다")
    void getRecommendationsByAnchor_intimacy_기반_파라미터_검증() {
        // given
        Long userId = 1L, anchorId = 10L;
        double intimacy = 0.5; // limit=7, threshold=3
        given(friendshipRepository.findById(Friendship.generateCompositeId(userId, anchorId)))
                .willReturn(Optional.of(friendship));
        given(friendship.getIntimacy()).willReturn(intimacy);
        given(socialExpansionRepository.getRecommendedNetworkByAnchor(userId, anchorId, 3, 7))
                .willReturn(List.of());

        // when
        service.getRecommendationsByAnchor(userId, anchorId);

        // then
        verify(socialExpansionRepository).getRecommendedNetworkByAnchor(userId, anchorId, 3, 7);
    }

    @Test
    @DisplayName("getRecommendationsByAnchor: 친구 관계가 없으면 FriendshipNotFoundException을 던진다")
    void getRecommendationsByAnchor_친구가_아닌_경우_예외() {
        // given
        Long userId = 1L, anchorId = 10L;
        given(friendshipRepository.findById(Friendship.generateCompositeId(userId, anchorId)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getRecommendationsByAnchor(userId, anchorId))
                .isInstanceOf(FriendshipNotFoundException.class);
    }

    @Test
    @DisplayName("getRecommendationsByAnchor: Repository 결과를 그대로 반환한다")
    void getRecommendationsByAnchor_결과_반환() {
        // given
        Long userId = 1L, anchorId = 10L;
        List<AnchorExpansionResult> expected = List.of(
                new AnchorExpansionResult(200L, "유저B")
        );
        given(friendshipRepository.findById(Friendship.generateCompositeId(userId, anchorId)))
                .willReturn(Optional.of(friendship));
        given(friendship.getIntimacy()).willReturn(0.9);
        given(socialExpansionRepository.getRecommendedNetworkByAnchor(eq(userId), eq(anchorId), anyInt(), anyInt()))
                .willReturn(expected);

        // when
        List<AnchorExpansionResult> result = service.getRecommendationsByAnchor(userId, anchorId);

        // then
        assertThat(result).isEqualTo(expected);
    }
}

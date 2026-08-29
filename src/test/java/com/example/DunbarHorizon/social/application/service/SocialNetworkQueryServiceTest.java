package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.application.port.out.SocialNetworkRepository;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import com.example.DunbarHorizon.social.domain.friend.Friendship;
import com.example.DunbarHorizon.social.domain.friend.SocialNetworkExposurePolicy;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SocialNetworkQueryServiceTest {

    @Mock
    private SocialNetworkRepository socialNetworkRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private Friendship friendship;

    @Mock
    private SocialNetworkExposurePolicy exposurePolicy;

    @InjectMocks
    private SocialNetworkQueryService service;

    @Test
    @DisplayName("getFriendsNetwork: 기본 네트워크 결과를 반환한다")
    void getFriendsNetwork_결과를_반환한다() {
        List<NodeGraphResult> expected = List.of(
                new NodeGraphResult(10L, 0.7, List.of(new NodeEdgeResult(20L, 0.85, 0.3)))
        );
        given(socialNetworkRepository.getDefaultNetworkGraph(1L, DunbarCircle.KINSHIP, 5, 10))
                .willReturn(expected);

        List<NodeGraphResult> result = service.getFriendsNetwork(1L, DunbarCircle.KINSHIP);

        verify(socialNetworkRepository).getDefaultNetworkGraph(1L, DunbarCircle.KINSHIP, 5, 10);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("getLabelNetwork: 라벨 네트워크는 DUNBAR 크기로 조회한다")
    void getLabelNetwork_결과를_반환한다() {
        given(socialNetworkRepository.getLabelCustomNetwork(1L, "label-1", DunbarCircle.DUNBAR, 5, 10))
                .willReturn(List.of());

        List<NodeGraphResult> result = service.getLabelNetwork(1L, "label-1");

        verify(socialNetworkRepository).getLabelCustomNetwork(1L, "label-1", DunbarCircle.DUNBAR, 5, 10);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getNetworkEdges: baseNetworkFriendIds가 비어있으면 Repository를 호출하지 않는다")
    void getNetworkEdges_baseNetworkFriendIds가_비어있으면_빈_리스트() {
        assertThat(service.getNetworkEdges(1L, 10L, null)).isEmpty();
        assertThat(service.getNetworkEdges(1L, 10L, List.of())).isEmpty();

        verifyNoInteractions(socialNetworkRepository, friendshipRepository);
    }

    @Test
    @DisplayName("getNetworkEdges: 직접 친구이면 정책 limit으로 1-hop 엣지를 조회한다")
    void getNetworkEdges_직접_친구() {
        List<Long> baseNetworkFriendIds = List.of(20L, 30L);
        List<MutualFriendEdgeResult> expected = List.of(new MutualFriendEdgeResult(10L, 20L, 0.6));
        given(friendshipRepository.findById(Friendship.generateCompositeId(1L, 10L)))
                .willReturn(Optional.of(friendship));
        given(friendship.getIntimacy()).willReturn(0.5);
        given(exposurePolicy.directFriendEdgeLimit(0.5)).willReturn(7);
        given(socialNetworkRepository.getDirectFriendEdgesForTarget(1L, 10L, baseNetworkFriendIds, 7)).willReturn(expected);

        List<MutualFriendEdgeResult> result = service.getNetworkEdges(1L, 10L, baseNetworkFriendIds);

        verify(exposurePolicy).directFriendEdgeLimit(0.5);
        verify(socialNetworkRepository).getDirectFriendEdgesForTarget(1L, 10L, baseNetworkFriendIds, 7);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("getNetworkEdges: 직접 친구가 아니면 정책 limit으로 2-hop 엣지를 조회한다")
    void getNetworkEdges_직접_친구가_아니면_2홉() {
        List<Long> baseNetworkFriendIds = List.of(20L, 30L);
        List<MutualFriendEdgeResult> expected = List.of(new MutualFriendEdgeResult(10L, 20L, null));
        given(friendshipRepository.findById(Friendship.generateCompositeId(1L, 10L)))
                .willReturn(Optional.empty());
        given(exposurePolicy.twoHopContactEdgeLimit()).willReturn(5);
        given(socialNetworkRepository.getTwoHopContactEdgesForTarget(1L, 10L, baseNetworkFriendIds, 5)).willReturn(expected);

        List<MutualFriendEdgeResult> result = service.getNetworkEdges(1L, 10L, baseNetworkFriendIds);

        verify(exposurePolicy).twoHopContactEdgeLimit();
        verify(socialNetworkRepository).getTwoHopContactEdgesForTarget(1L, 10L, baseNetworkFriendIds, 5);
        assertThat(result).isEqualTo(expected);
    }
}

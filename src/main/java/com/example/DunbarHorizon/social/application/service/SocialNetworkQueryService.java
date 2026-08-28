package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import com.example.DunbarHorizon.social.application.port.in.SocialNetworkQueryUseCase;
import com.example.DunbarHorizon.social.application.port.out.SocialNetworkRepository;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import com.example.DunbarHorizon.social.domain.friend.Friendship;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialNetworkQueryService implements SocialNetworkQueryUseCase {

    private static final int PRUNING_EDGE_MIN   = 5;
    private static final int PRUNING_EDGE_RANGE = 10;
    private static final int DIRECT_EDGE_BASE_LIMIT          = 5;
    private static final int DIRECT_EDGE_INTIMACY_MULTIPLIER = 5;
    private static final int TWO_HOP_CONTACT_EDGE_QUOTA      = 5;

    private final SocialNetworkRepository socialNetworkRepository;
    private final FriendshipRepository friendshipRepository;

    @Override
    public List<NodeGraphResult> getFriendsNetwork(Long userId, DunbarCircle circleSize) {
        return socialNetworkRepository.getDefaultNetworkGraph(userId, circleSize, PRUNING_EDGE_MIN, PRUNING_EDGE_RANGE);
    }

    @Override
    public List<NodeGraphResult> getLabelNetwork(Long userId, String labelId) {
        return socialNetworkRepository.getLabelCustomNetwork(userId, labelId, DunbarCircle.DUNBAR, PRUNING_EDGE_MIN, PRUNING_EDGE_RANGE);
    }

    @Neo4jTransactional(readOnly = true)
    @Override
    public List<MutualFriendEdgeResult> getNetworkEdges(Long userId, Long targetId, List<Long> baseNetworkFriendIds) {
        if (baseNetworkFriendIds == null || baseNetworkFriendIds.isEmpty()) return List.of();
        return friendshipRepository.findById(Friendship.generateCompositeId(userId, targetId))
                .map(friendship -> {
                    int dynamicLimit = (int) (DIRECT_EDGE_BASE_LIMIT
                            + friendship.getIntimacy() * DIRECT_EDGE_INTIMACY_MULTIPLIER);
                    return socialNetworkRepository.getDirectFriendEdgesForTarget(
                            userId, targetId, baseNetworkFriendIds, dynamicLimit);
                })
                .orElseGet(() -> socialNetworkRepository.getTwoHopContactEdgesForTarget(
                        userId, targetId, baseNetworkFriendIds, TWO_HOP_CONTACT_EDGE_QUOTA));
    }
}

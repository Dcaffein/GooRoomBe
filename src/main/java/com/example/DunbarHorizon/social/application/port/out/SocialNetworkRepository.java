package com.example.DunbarHorizon.social.application.port.out;

import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;

import java.util.List;

public interface SocialNetworkRepository {
    List<NodeGraphResult> getDefaultNetworkGraph(Long userId, DunbarCircle circleSize, int pruningMin, int pruningRange);
    List<NodeGraphResult> getLabelCustomNetwork(Long userId, String labelId, DunbarCircle circleSize, int pruningMin, int pruningRange);

    List<MutualFriendEdgeResult> getDirectFriendEdgesForTarget(Long userId, Long targetId, List<Long> baseNetworkFriendIds, int dynamicLimit);
    List<MutualFriendEdgeResult> getTwoHopContactEdgesForTarget(Long userId, Long targetId, List<Long> baseNetworkFriendIds, int strangerLimit);
}

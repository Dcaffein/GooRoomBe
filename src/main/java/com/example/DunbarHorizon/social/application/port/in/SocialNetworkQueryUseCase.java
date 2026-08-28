package com.example.DunbarHorizon.social.application.port.in;

import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;

import java.util.List;

public interface SocialNetworkQueryUseCase {
    List<NodeGraphResult> getFriendsNetwork(Long userId, DunbarCircle circleSize);
    List<NodeGraphResult> getLabelNetwork(Long userId, String labelId);
    List<MutualFriendEdgeResult> getNetworkEdges(Long userId, Long targetId, List<Long> baseNetworkFriendIds);
}

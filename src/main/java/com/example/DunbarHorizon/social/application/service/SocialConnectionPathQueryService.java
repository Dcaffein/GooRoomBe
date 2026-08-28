package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;
import com.example.DunbarHorizon.social.application.port.in.SocialConnectionPathQueryUseCase;
import com.example.DunbarHorizon.social.application.port.out.SocialConnectionPathRepository;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Neo4jTransactional(readOnly = true)
public class SocialConnectionPathQueryService implements SocialConnectionPathQueryUseCase {

    private static final int INTERMEDIARY_LIMIT = 3;

    private final SocialConnectionPathRepository connectionPathRepository;
    private final FriendshipRepository friendshipRepository;

    @Override
    public ConnectionPathResult getConnectionPath(Long myId, Long targetId) {
        if (myId.equals(targetId)) {
            return new ConnectionPathResult(false, 0, List.of());
        }
        boolean direct = friendshipRepository.existsFriendshipBetween(myId, targetId);
        // 이미 친구인 대상의 프로필에서도 공통 친구 수는 연결 맥락이므로 direct 여부와 무관하게 조회한다
        ConnectionPathResult.Intermediaries intermediaries =
                connectionPathRepository.findIntermediaries(myId, targetId, INTERMEDIARY_LIMIT);
        return new ConnectionPathResult(direct, intermediaries.totalCount(), intermediaries.items());
    }
}

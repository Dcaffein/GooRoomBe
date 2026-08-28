package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.port.in.FriendRequestQueryUseCase;
import com.example.DunbarHorizon.social.application.dto.result.FriendRequestResult;
import com.example.DunbarHorizon.social.application.dto.FriendRequestDirection;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestInvalidException;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import com.example.DunbarHorizon.global.annotation.Neo4jTransactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Neo4jTransactional(readOnly = true)
public class FriendRequestQueryService implements FriendRequestQueryUseCase {
    private final FriendRequestRepository friendRequestRepository;

    @Override
    public List<FriendRequestResult> getRequests(
            Long userId, FriendRequestDirection direction, FriendRequestStatus status) {
        FriendRequestStatus queryStatus = status == null ? FriendRequestStatus.PENDING : status;

        if (direction == FriendRequestDirection.SENT) {
            if (status != null) {
                throw new FriendRequestInvalidException(
                        "sent 조회에는 status를 사용할 수 없습니다."
                );
            }
            return findSentRequests(userId);
        }

        if (queryStatus != FriendRequestStatus.PENDING && queryStatus != FriendRequestStatus.HIDDEN) {
            throw new FriendRequestInvalidException(
                    "received 조회에는 PENDING 또는 HIDDEN만 사용할 수 있습니다."
            );
        }

        return friendRequestRepository.findAllByReceiver_IdAndStatus(userId, queryStatus)
                .stream()
                .map(FriendRequestResult::from)
                .toList();
    }

    private List<FriendRequestResult> findSentRequests(Long userId) {
        return friendRequestRepository.findAllByRequester_IdAndStatus(userId, FriendRequestStatus.PENDING)
                .stream()
                .map(FriendRequestResult::from)
                .toList();
    }
}

package com.example.DunbarHorizon.social.domain.friend;

import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestAuthorizationException;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestInvalidException;

public enum FriendRequestStatus {

    PENDING {
        @Override
        public FriendRequestStatus update(FriendRequest request, Long userId) {
            if (request.getStatus() != HIDDEN) {
                return throwInvalidException(
                        "[%s] 상태에서 [%s] 상태로 변경할 수 없습니다.",
                        request.getStatus(), PENDING
                );
            }
            validateReceiver(request, userId);
            return this;
        }

        @Override
        public void cancel(FriendRequest request, Long userId) {
            validateRequester(request, userId);
        }
    },

    ACCEPTED {
        @Override
        public FriendRequestStatus update(FriendRequest request, Long userId) {
            if (request.getStatus() != PENDING && request.getStatus() != HIDDEN) {
                return throwInvalidException(
                        "[%s] 상태에서 [%s] 상태로 변경할 수 없습니다.",
                        request.getStatus(), ACCEPTED
                );
            }
            validateReceiver(request, userId);
            return this;
        }
    },

    HIDDEN {
        @Override
        public FriendRequestStatus update(FriendRequest request, Long userId) {
            if (request.getStatus() != PENDING) {
                return throwInvalidException(
                        "[%s] 상태에서 [%s] 상태로 변경할 수 없습니다.",
                        request.getStatus(), HIDDEN
                );
            }
            validateReceiver(request, userId);
            return this;
        }
    };

    public FriendRequestStatus update(FriendRequest request, Long userId) {
        return throwInvalidException(
                "[%s] 상태에서 [%s] 상태로 변경할 수 없습니다.",
                request.getStatus(), this
        );
    }

    public void cancel(FriendRequest request, Long userId) {
        throwInvalidException("[%s] 상태에서는 취소할 수 없습니다.", this);
    }

    protected void validateReceiver(FriendRequest request, Long userId) {
        if (!request.getReceiver().getId().equals(userId)) {
            throw new FriendRequestAuthorizationException(request.getId(), userId);
        }
    }

    protected void validateRequester(FriendRequest request, Long userId) {
        if (!request.getRequester().getId().equals(userId)) {
            throw new FriendRequestAuthorizationException(request.getId(), userId);
        }
    }

    private static FriendRequestStatus throwInvalidException(String message, Object... args) {
        throw new FriendRequestInvalidException(String.format(message, args));
    }
}

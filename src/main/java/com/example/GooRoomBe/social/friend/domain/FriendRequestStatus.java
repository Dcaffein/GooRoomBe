package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.friend.exception.FriendRequestAuthorizationException;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotPendingException;

public enum FriendRequestStatus {
    PENDING{
        @Override
        public void transit(FriendRequest friendRequest, FriendRequestStatus newStatus, String currentUserId) {
            if(!friendRequest.getReceiver().getId().equals(currentUserId) ){
                throw new FriendRequestAuthorizationException(friendRequest.getId(), currentUserId);
            }

            if(newStatus == ACCEPTED ){
                friendRequest.setStatus(FriendRequestStatus.ACCEPTED);
                friendRequest.completeAcceptance();
            } else if(newStatus == HIDDEN ){
                friendRequest.setStatus(FriendRequestStatus.HIDDEN);
            }
        }

        @Override
        public void validateCancel(FriendRequest request, String currentUserId) {
            if (!request.getRequester().getId().equals(currentUserId)) {
                throw new FriendRequestAuthorizationException(request.getId(), currentUserId);
            }
        }
    },

    ACCEPTED{
        @Override
        public void transit(FriendRequest friendRequest, FriendRequestStatus newStatus, String currentUserId) {
            if(newStatus == NOTIFIED){
                friendRequest.setStatus(FriendRequestStatus.NOTIFIED);
            }
        }
    },

    //will be deleted by scheduler
    NOTIFIED{
        @Override
        public void transit(FriendRequest friendRequest, FriendRequestStatus newStatus, String currentUserId) {}
    },


    HIDDEN{
        @Override
        public void transit(FriendRequest friendRequest, FriendRequestStatus newStatus, String currentUserId) {
            if(!friendRequest.getReceiver().getId().equals(currentUserId) ){
                throw new FriendRequestAuthorizationException(friendRequest.getId(), currentUserId);
            }

            if(newStatus == PENDING){
                friendRequest.setStatus(FriendRequestStatus.PENDING);
            }
        }
    };

    public abstract void transit(FriendRequest friendRequest, FriendRequestStatus newStatus, String currentUserId);
    public void validateCancel(FriendRequest request, String currentUserId) {
        throw new FriendRequestNotPendingException(request.getId());
    }
}
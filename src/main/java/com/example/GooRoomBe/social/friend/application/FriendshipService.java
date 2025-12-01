package com.example.GooRoomBe.social.friend.application;

import com.example.GooRoomBe.social.friend.api.dto.FriendUpdateRequestDto;
import com.example.GooRoomBe.social.friend.domain.event.FriendShipDeletedEvent;
import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.infrastructure.FriendshipPort;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@Service
public class FriendshipService {
    private final FriendshipPort friendshipPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void updateFriendProps(String currentUserId, String friendId, FriendUpdateRequestDto dto) {
        Friendship friendship = friendshipPort.getFriendship(currentUserId, friendId);

        applyIfPresent(dto.friendAlias(), alias -> friendship.updateFriendAlias(currentUserId, alias));
        applyIfPresent(dto.onIntroduce(), onIntroduce -> friendship.updateOnIntroduce(currentUserId, onIntroduce));

        friendshipPort.save(friendship);
    }

    @Transactional
    public void deleteFriendShip(String currentUserId, String friendId) {
        Friendship friendShip = friendshipPort.getFriendship(currentUserId,friendId);

        friendShip.checkDeletable(currentUserId);
        List<SocialUser> userIds = friendShip.getUsers().stream().toList();
        friendshipPort.delete(friendShip);

        eventPublisher.publishEvent(new FriendShipDeletedEvent(userIds.getFirst().getId(), userIds.getLast().getId()));
    }

    private <T> void applyIfPresent(T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }
}

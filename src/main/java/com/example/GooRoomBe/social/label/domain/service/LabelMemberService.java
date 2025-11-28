package com.example.GooRoomBe.social.label.domain.service;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import com.example.GooRoomBe.social.friend.infrastructure.FriendshipRepository;
import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.label.exception.InvalidLabelMemberException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LabelMemberService {
    private final FriendshipRepository friendShipRepository;

    public void addNewMember(Label label, SocialUser potentialNewMember) {
        if (!friendShipRepository.existsFriendshipBetween(label.getOwner().getId(), potentialNewMember.getId())) {
            throw new FriendshipNotFoundException(label.getOwner().getId(), potentialNewMember.getId());
        }
        label.applyNewMember(potentialNewMember);
    }

    public void replaceMembers(Label label, List<String> newMemberIds) {
        String ownerId = label.getOwner().getId();

        if (newMemberIds == null || newMemberIds.isEmpty()) {
            label.replaceMembers(Set.of());
            return;
        }

        Set<SocialUser> validMembers = this.getValidMembers(ownerId, newMemberIds);
        Set<String> requestedIds = new HashSet<>(newMemberIds);

        if (validMembers.size() != requestedIds.size()) {
            Set<String> validIds = validMembers.stream()
                    .map(SocialUser::getId)
                    .collect(Collectors.toSet());

            List<String> invalidIds = requestedIds.stream()
                    .filter(id -> !validIds.contains(id))
                    .toList();

            throw new InvalidLabelMemberException(invalidIds);
        }

        label.replaceMembers(validMembers);
    }

    private Set<SocialUser> getValidMembers(String ownerId, List<String> potentialMemberIds) {
        Set<Friendship> friendships = friendShipRepository.filterFriendsFromIdList(ownerId, potentialMemberIds);
        return friendships.stream()
                .map(friendShip -> friendShip.getFriend(ownerId))
                .collect(Collectors.toSet());
    }
}

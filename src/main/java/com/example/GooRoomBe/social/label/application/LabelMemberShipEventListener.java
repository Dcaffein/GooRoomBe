package com.example.GooRoomBe.social.label.application;

import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.friend.domain.event.FriendShipDeletedEvent;
import com.example.GooRoomBe.social.label.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabelMemberShipEventListener {

    private final LabelRepository labelRepository;
    private final SocialUserRepository socialUserRepository;

    @EventListener
    @Transactional
    public void handleFriendShipDeleted(FriendShipDeletedEvent event) {
        String userA_Id = event.userAId();
        String userB_Id = event.userBId();

        removeFriendFromLabels(userA_Id, userB_Id);
        removeFriendFromLabels(userB_Id, userA_Id);
    }

    private void removeFriendFromLabels(String ownerId, String memberIdToRemove) {
        SocialUser owner = socialUserRepository.getUser(ownerId);

        List<Label> labels = labelRepository.findAllByOwner_Id(owner.getId());

        SocialUser memberToRemove = socialUserRepository.getUser(memberIdToRemove);

        for (Label label : labels) {
            log.debug("Label '{}'에서 멤버 '{}' 제거 시도", label.getLabelName(), memberIdToRemove);
            label.removeMember(memberToRemove);
        }
    }
}

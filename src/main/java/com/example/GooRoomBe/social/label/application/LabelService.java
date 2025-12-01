package com.example.GooRoomBe.social.label.application;

import com.example.GooRoomBe.social.label.api.dto.LabelUpdateRequestDto;
import com.example.GooRoomBe.social.label.exception.LabelNotFoundException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.label.domain.LabelFactory;
import com.example.GooRoomBe.social.label.domain.service.LabelMemberService;
import com.example.GooRoomBe.social.label.domain.service.LabelNameService;
import com.example.GooRoomBe.social.label.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;


@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LabelService {
    private final LabelRepository labelRepository;
    private final LabelFactory labelFactory;
    private final LabelNameService labelNameService;
    private final LabelMemberService labelMemberService;
    private final SocialUserRepository socialUserRepository;

    @Transactional
    public Label createLabel(String currentUserId, String labelName, boolean exposure) {
        Label newLabel = labelFactory.createLabel(currentUserId, labelName, exposure);
        return labelRepository.save(newLabel);
    }

    @Transactional
    public void deleteLabel(String currentUserId, String labelId) {
        Label label = getLabel(labelId);
        if(label.getOwner().getId().equals(currentUserId)){
            labelRepository.delete(label);
        }
    }

    @Transactional
    public void addMember(String labelId, String newMemberId) {
        Label label = getLabel(labelId);
        SocialUser newMember = socialUserRepository.getUser(newMemberId);
        labelMemberService.addNewMember(label, newMember);
        labelRepository.save(label);
    }


    @Transactional
    public void removeMember(String labelId, String memberIdToRemove) {
        Label label = getLabel(labelId);
        SocialUser memberToRemove = socialUserRepository.getUser(memberIdToRemove);
        label.removeMember(memberToRemove);
        labelRepository.save(label);
    }

    @Transactional
    public void replaceMembers(String labelId, List<String> potentialMemberIds) {
        Label label = getLabel(labelId);
        labelMemberService.replaceMembers(label, potentialMemberIds);
        labelRepository.save(label);
    }

    @Transactional
    public void updateLabel(String labelId, String currentUserId, LabelUpdateRequestDto dto){
        Label label = getLabel(labelId);

        applyIfPresent(dto.labelName(), name -> labelNameService.changeLabelName(label, name));
        applyIfPresent(dto.exposure(), exposure -> label.updateExposure(currentUserId, exposure));

        labelRepository.save(label);
    }

    private <T> void applyIfPresent(T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }

    private Label getLabel(String labelId) {
        return labelRepository.findById(labelId).orElseThrow(()->new LabelNotFoundException(labelId));
    }
}

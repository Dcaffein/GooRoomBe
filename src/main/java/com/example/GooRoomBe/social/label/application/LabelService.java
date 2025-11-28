package com.example.GooRoomBe.social.label.application;

import com.example.GooRoomBe.social.label.api.dto.LabelUpdateRequestDto;
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
        Label label = labelRepository.findById(labelId).orElseThrow();
        if(label.getOwner().getId().equals(currentUserId)){
            labelRepository.delete(label);
        }
    }

    @Transactional
    public void addMember(String labelId, String newMemberId) {
        Label label = labelRepository.findById(labelId).orElseThrow();
        SocialUser newMember = socialUserRepository.getUser(label.getOwner().getId());
        labelMemberService.addNewMember(label, newMember);
        labelRepository.save(label);
    }


    @Transactional
    public void removeMember(String LabelId, String memberIdToRemove) {
        Label label = labelRepository.findById(LabelId).orElseThrow();
        SocialUser memberToRemove = socialUserRepository.getUser(memberIdToRemove);
        label.removeMember(memberToRemove);
        labelRepository.save(label);
    }

    @Transactional
    public void replaceMembers(String labelId, List<String> potentialMemberIds) {
        Label label = labelRepository.findById(labelId).orElseThrow();
        labelMemberService.replaceMembers(label, potentialMemberIds);
        labelRepository.save(label);
    }

    @Transactional
    public void updateLabel(String labelId, String currentUserId, LabelUpdateRequestDto dto){
        Label label = labelRepository.findById(labelId).orElseThrow();

        applyIfPresent(dto.labelName(), name -> labelNameService.changeLabelName(label, name));
        applyIfPresent(dto.exposure(), exposure -> label.updateExposure(currentUserId, exposure));

        labelRepository.save(label);
    }

    private <T> void applyIfPresent(T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }
}

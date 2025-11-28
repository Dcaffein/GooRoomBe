package com.example.GooRoomBe.social.label.domain;

import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import com.example.GooRoomBe.social.label.domain.service.LabelNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LabelFactory {

    private final SocialUserRepository socialUserRepository;
    private final LabelNameService labelNameService;

    public Label createLabel(String ownerId, String labelName, boolean exposure) {

        labelNameService.validateLabelNameUniqueness(ownerId, labelName);

        SocialUser owner = socialUserRepository.getUser(ownerId);

        return new Label(owner, labelName, exposure);
    }
}
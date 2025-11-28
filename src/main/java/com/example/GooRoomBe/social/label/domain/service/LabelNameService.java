package com.example.GooRoomBe.social.label.domain.service;

import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.label.exception.LabelNameDuplicateException;
import com.example.GooRoomBe.social.label.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LabelNameService {
    private final LabelRepository labelRepository;

    public void changeLabelName(Label targetLabel, String newLabelName) {
        if (targetLabel.getLabelName().equals(newLabelName)) {
            return;
        }

        String ownerId = targetLabel.getOwner().getId();
        validateLabelNameUniqueness(ownerId, newLabelName);
        targetLabel.applyNewLabelName(newLabelName);
    }


    public void validateLabelNameUniqueness(String ownerId, String labelName) {
        if (labelRepository.existsByOwner_IdAndLabelName(ownerId, labelName)) {
            throw new LabelNameDuplicateException(ownerId, labelName);
        }
    }
}
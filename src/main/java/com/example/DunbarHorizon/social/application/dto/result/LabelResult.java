package com.example.DunbarHorizon.social.application.dto.result;

import com.example.DunbarHorizon.social.domain.label.Label;

public record LabelResult(
        String id,
        String labelName,
        int memberCount
) {
    public static LabelResult from(Label label) {
        return new LabelResult(
                label.getId(),
                label.getLabelName(),
                label.getMembers().size()
        );
    }
}

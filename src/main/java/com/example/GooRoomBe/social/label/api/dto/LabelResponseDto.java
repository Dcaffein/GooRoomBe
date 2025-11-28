package com.example.GooRoomBe.social.label.api.dto;

import com.example.GooRoomBe.social.common.dto.SocialMemberResponseDto;
import com.example.GooRoomBe.social.label.domain.Label;

import java.util.List;

public record LabelResponseDto(
        String id,
        String labelName,
        boolean exposure,
        List<SocialMemberResponseDto> members // 멤버 목록 상세
) {
    public static LabelResponseDto from(Label entity) {
        return new LabelResponseDto(
                entity.getId(),
                entity.getLabelName(),
                entity.isExposure(),
                entity.getMembers().stream()
                        .map(SocialMemberResponseDto::from)
                        .toList()
        );
    }
}
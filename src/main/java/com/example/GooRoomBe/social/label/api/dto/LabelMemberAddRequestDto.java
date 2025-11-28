package com.example.GooRoomBe.social.label.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LabelMemberAddRequestDto(
        @NotBlank(message = "추가할 멤버 ID는 필수입니다.")
        String memberId
) {}
package com.example.GooRoomBe.social.label.api.dto;

import jakarta.validation.constraints.Size;

public record LabelUpdateRequestDto(
        @Size(max = 20, message = "라벨 이름은 20자 이내여야 합니다.")
        String labelName,

        Boolean exposure
) {}
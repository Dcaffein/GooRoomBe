package com.example.GooRoomBe.social.label.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabelCreateRequestDto(
        @NotBlank(message = "라벨 이름은 필수입니다.")
        @Size(max = 20, message = "라벨 이름은 20자 이내여야 합니다.")
        String labelName,

        @NotNull(message = "노출 여부(exposure)는 필수입니다.")
        Boolean exposure
) {}
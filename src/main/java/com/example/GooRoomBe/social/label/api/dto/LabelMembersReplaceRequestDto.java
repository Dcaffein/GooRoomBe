package com.example.GooRoomBe.social.label.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record LabelMembersReplaceRequestDto(
        @NotNull(message = "멤버 ID 리스트는 필수입니다. (빈 리스트 허용)")
        List<String> memberIds
) {}
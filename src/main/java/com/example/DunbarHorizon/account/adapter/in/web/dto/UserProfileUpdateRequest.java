package com.example.DunbarHorizon.account.adapter.in.web.dto;

import com.example.DunbarHorizon.account.domain.policy.NicknamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH,
                message = NicknamePolicy.LENGTH_MESSAGE)
        String nickname,

        String profileImageKey
) {}

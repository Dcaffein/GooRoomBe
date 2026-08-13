package com.example.DunbarHorizon.account.adapter.in.web.dto;

import com.example.DunbarHorizon.account.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = User.NICKNAME_MIN_LENGTH, max = User.NICKNAME_MAX_LENGTH,
                message = User.NICKNAME_LENGTH_MESSAGE)
        String nickname,

        String profileImageKey
) {}

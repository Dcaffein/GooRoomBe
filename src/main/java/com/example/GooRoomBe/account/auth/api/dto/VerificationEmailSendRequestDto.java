package com.example.GooRoomBe.account.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerificationEmailSendRequestDto(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "[System Error] redirectPage 파라미터가 누락되었습니다.")
        String redirectPage
) {}
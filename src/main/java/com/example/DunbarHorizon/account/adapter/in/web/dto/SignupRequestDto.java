package com.example.DunbarHorizon.account.adapter.in.web.dto;

import com.example.DunbarHorizon.account.domain.policy.NicknamePolicy;
import com.example.DunbarHorizon.account.domain.policy.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 완료 요청. 이메일은 받지 않는다 — 토큰이 가리키는 값을 서버가 쓴다.
 * 클라이언트가 이메일을 함께 보내면 토큰과 다른 주소로 계정을 만들 여지가 생긴다.
 */
public record SignupRequestDto(
        @NotBlank(message = "인증 토큰은 필수입니다.")
        String token,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH,
                message = NicknamePolicy.LENGTH_MESSAGE)
        String nickname,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password
) {}

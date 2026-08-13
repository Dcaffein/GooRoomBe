package com.example.DunbarHorizon.account.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 착지 경로를 받지 않는다. 이 엔드포인트는 인증이 없어 누구나 호출할 수 있으므로,
 * 링크 경로를 본문에서 받으면 공격자가 임의 주소로 "우리 도메인에서 발송된 진짜 메일"에
 * 자기가 정한 경로와 쿼리를 담을 수 있다. 목적지는 프론트가 origin 안에서 정한다.
 */
public record VerificationEmailRequestDto(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {}

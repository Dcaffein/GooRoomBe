package com.example.DunbarHorizon.account.adapter.in.web.dto;

/**
 * 자격증명 입력 폼을 그리기 전 토큰 유효성 확인 결과. 어떤 이메일로 가입 중인지 화면에 표시한다.
 */
public record VerificationTokenResponse(String email) {}

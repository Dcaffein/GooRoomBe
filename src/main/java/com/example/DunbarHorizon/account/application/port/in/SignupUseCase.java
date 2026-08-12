package com.example.DunbarHorizon.account.application.port.in;

import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import com.example.DunbarHorizon.account.domain.User;

public interface SignupUseCase {

    /**
     * 이메일 소유 증명이 끝난 뒤 자격증명을 받아 계정을 만든다.
     * 증명을 마친 사람이므로 생성과 동시에 토큰을 발급한다.
     */
    AuthTokenResult signup(String token, String password, String nickname);

    User registerOAuthUser(String email, String nickname, AuthProvider provider, String providerId);
}

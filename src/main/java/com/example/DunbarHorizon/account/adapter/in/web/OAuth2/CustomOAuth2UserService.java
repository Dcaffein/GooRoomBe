package com.example.DunbarHorizon.account.adapter.in.web.OAuth2;

import com.example.DunbarHorizon.account.adapter.in.web.OAuth2.OAuth2UserInfo.OAuth2UserInfo;
import com.example.DunbarHorizon.account.application.port.in.SignupUseCase;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import com.example.DunbarHorizon.account.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SignupUseCase signupUseCase;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        AuthProvider provider = AuthProvider.valueOf(userRequest.getClientRegistration().getRegistrationId().toUpperCase());

        OAuth2UserInfo userInfo = OAuth2UserInfoMapper.parse(provider, oAuth2User.getAttributes());

        // 공급자가 소유를 검증하지 않은 주소는 신원 키로 쓰지 않는다. 이 검사가 없으면
        // 남의 이메일을 적어 넣은 공급자 계정으로 기존 계정에 연동될 수 있다.
        if (!userInfo.isEmailVerified()) {
            log.warn("[oauth2] 공급자가 검증하지 않은 이메일로 로그인 시도. provider={}", provider);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified"), "공급자에서 이메일 인증이 완료되지 않은 계정입니다.");
        }

        User savedUser = signupUseCase.registerOAuthUser(
                userInfo.getEmail(),
                userInfo.getName(),
                provider,
                userInfo.getId()
        );

        return new CustomOAuth2User(savedUser, oAuth2User.getAttributes());
    }
}
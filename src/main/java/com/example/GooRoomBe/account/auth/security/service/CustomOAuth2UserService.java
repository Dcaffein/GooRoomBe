package com.example.GooRoomBe.account.auth.security.service;

import com.example.GooRoomBe.account.auth.domain.Auth;
import com.example.GooRoomBe.account.auth.domain.OAuth;
import com.example.GooRoomBe.account.auth.domain.Provider;
import com.example.GooRoomBe.account.auth.security.principal.CustomOAuth2User;
import com.example.GooRoomBe.account.auth.security.principal.oauthInfo.OAuth2UserInfo;
import com.example.GooRoomBe.account.auth.repository.OAuthRepository;
import com.example.GooRoomBe.account.user.domain.User;
import com.example.GooRoomBe.account.user.domain.UserFactory;
import com.example.GooRoomBe.account.user.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthRepository oAuthRepository;
    private final UserRepository userRepository;
    private final UserFactory userFactory;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        Provider provider = Provider.valueOf(registrationId.toUpperCase());
        OAuth2UserInfo oAuth2UserInfo = provider.createOAuth2UserInfo(oAuth2User.getAttributes());

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuth oauth = findOrCreateOAuthUser(provider, oAuth2UserInfo);

        return new CustomOAuth2User(
                oAuth2User.getAuthorities(),
                oAuth2User.getAttributes(),
                userNameAttributeName,
                oauth
        );
    }

    private OAuth findOrCreateOAuthUser(Provider provider, OAuth2UserInfo userInfo) {
        String providerId = userInfo.getProviderId();
        String email = userInfo.getEmail();

        // (Provider, ProviderId)로 기존 OAuth 인증 정보 조회
        Optional<OAuth> oAuthOptional = oAuthRepository.findByProviderAndProviderId(provider, providerId);

        // 이미 연동된 OAuth 유저
        if (oAuthOptional.isPresent()) {
            return oAuthOptional.get();
        }

        // 처음 보는 OAuth 로그인시도
        else {
            Optional<User> existingUserByEmail = userRepository.findByEmail(email);
            User user;

            //  해당 이메일을 가진 계정 존재 -> 계정 연동 및 활성화
            if (existingUserByEmail.isPresent()) {

                user = existingUserByEmail.get();
                user.verify();
            }
            // 완전 신규 유저 -> 회원 가입
            else {
                user = userFactory.createActiveUser(
                        userInfo.getName(),
                        userInfo.getEmail()
                );
                userRepository.save(user);
            }

            // (공통) 새 OAuth 인증 정보를 생성하고 기존/신규 User에 연결
            OAuth newOAuth = createNewOAuthAccount(userInfo, provider, user);
            return oAuthRepository.save(newOAuth);
        }
    }

    private OAuth createNewOAuthAccount(OAuth2UserInfo userInfo, Provider provider, User user) {
        return OAuth.builder()
                .provider(provider)
                .providerId(userInfo.getProviderId())
                .user(user)
                .build();
    }
}
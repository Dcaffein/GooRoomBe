package com.example.GooRoomBe.account.auth.domain;

import com.example.GooRoomBe.account.auth.security.principal.oauthInfo.GoogleUserInfo;
import com.example.GooRoomBe.account.auth.security.principal.oauthInfo.OAuth2UserInfo;

import java.util.Map;
import java.util.function.Function;

public enum Provider {
    LOCAL(attributes -> { throw new UnsupportedOperationException("LOCAL Provider not supported for OAuth2"); }),
    GOOGLE(GoogleUserInfo::new);
    // NAVER(NaverUserInfo::new),
    // KAKAO(KakaoUserInfo::new);

    private final Function<Map<String, Object>, OAuth2UserInfo> userInfoFactoryMethod;

    /**
     * Map<String,Object> : attributes
     * @param userInfoFactoryMethod
     */
    Provider(Function <Map<String,Object>,OAuth2UserInfo> userInfoFactoryMethod) {
        this.userInfoFactoryMethod = userInfoFactoryMethod;
    }

    public OAuth2UserInfo createOAuth2UserInfo(Map<String, Object> attributes) {
        return userInfoFactoryMethod.apply(attributes);
    }
}
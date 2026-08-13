package com.example.DunbarHorizon.account.adapter.in.web.OAuth2.OAuth2UserInfo;

import java.util.Map;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(attributes.get("email_verified"));
    }


    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
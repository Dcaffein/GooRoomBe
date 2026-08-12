package com.example.DunbarHorizon.account.adapter.in.web.OAuth2.OAuth2UserInfo;

import java.util.Map;

public interface OAuth2UserInfo {
    String getId();
    String getName();
    String getEmail();

    /**
     * 공급자가 이 이메일의 소유를 검증했는지. 이 값이 거짓이면 이메일을 신원으로 쓸 수 없다.
     * 검증되지 않은 주소를 신원 키로 받아들이면 남의 이메일을 적어 넣은 계정으로 기존 계정에
     * 연동되는 길이 열린다.
     */
    boolean isEmailVerified();

    Map<String, Object> getAttributes();
}
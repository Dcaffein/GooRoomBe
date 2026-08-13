package com.example.DunbarHorizon.account.application.port.out;

public interface EmailPort {

    void sendSignupVerificationEmail(String email, String token);

    /**
     * 이미 가입된 이메일로 접수 요청이 왔을 때 보낸다.
     *
     * <p>가입 접수는 등록 여부와 무관하게 항상 같은 응답을 돌려주므로, 상황을 알리는 통로가
     * 이 메일뿐이다. 응답으로는 가입 여부를 알 수 없고 실제 이메일 주인만 알게 된다.
     */
    void sendAlreadyRegisteredEmail(String email);
}

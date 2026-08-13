package com.example.DunbarHorizon.account.application.port.in;

public interface VerificationUseCase {

    /**
     * 가입 접수. 이메일 등록 여부와 무관하게 같은 결과를 반환하며, 구분은 발송되는 메일 내용으로만 한다.
     */
    void requestVerification(String email, String redirectPage);

    String resolveEmail(String token);
}

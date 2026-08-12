package com.example.DunbarHorizon.account.application.service;

import com.example.DunbarHorizon.account.application.port.in.VerificationUseCase;
import com.example.DunbarHorizon.account.application.port.out.EmailPort;
import com.example.DunbarHorizon.account.domain.exception.InvalidVerificationTokenException;
import com.example.DunbarHorizon.account.domain.repository.PendingSignupRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import com.example.DunbarHorizon.global.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService implements VerificationUseCase {

    private final UserRepository userRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final EmailPort emailPort;

    /**
     * 가입 접수. 호출자는 이메일 등록 여부를 알 수 없다 — 어느 경우든 예외 없이 끝난다.
     * 구분은 오직 발송되는 메일 내용으로만 이뤄지므로, 상황을 아는 사람은 이메일 주인뿐이다.
     */
    @Override
    public void requestVerification(String email, String redirectPage) {
        if (userRepository.findByEmail(email).isPresent()) {
            emailPort.sendAlreadyRegisteredEmail(email);
            return;
        }

        String token = UuidUtil.createV7().toString();
        pendingSignupRepository.save(token, email);
        emailPort.sendSignupVerificationEmail(email, token, redirectPage);
    }

    @Override
    public String resolveEmail(String token) {
        return pendingSignupRepository.findEmailByToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);
    }
}

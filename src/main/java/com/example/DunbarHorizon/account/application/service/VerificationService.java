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

    @Override
    public void requestVerification(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            emailPort.sendAlreadyRegisteredEmail(email);
            return;
        }

        String token = UuidUtil.createV7().toString();
        pendingSignupRepository.save(token, email);
        emailPort.sendSignupVerificationEmail(email, token);
    }

    @Override
    public String resolveEmail(String token) {
        return pendingSignupRepository.findEmailByToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);
    }
}

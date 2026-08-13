package com.example.DunbarHorizon.account.adapter.out.encrypt;

import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.domain.HashedPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityPasswordAdapter implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public HashedPassword hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 비어있을 수 없습니다.");
        }
        return new HashedPassword(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, HashedPassword hashedPassword) {
        return hashedPassword != null
                && passwordEncoder.matches(rawPassword, hashedPassword.value());
    }
}

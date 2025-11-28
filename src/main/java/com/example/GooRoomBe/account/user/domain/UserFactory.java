package com.example.GooRoomBe.account.user.domain;

import com.example.GooRoomBe.account.user.exception.DuplicateEmailException;
import com.example.GooRoomBe.account.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFactory {

    private final UserRepository userRepository;

    private void validateEmailDuplication(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException(email);
        }
    }

    /**
     * 일반 가입 (이메일 인증 필요) 사용자를 생성
     * @param nickname 닉네임
     * @param email 이메일
     * @return User (status: UNVERIFIED)
     */
    public User createUnverifiedUser(String nickname, String email) {
        validateEmailDuplication(email);
        return new User(nickname, email, UserStatus.UNVERIFIED);
    }

    /**
     * OAuth 가입 또는 테스트용 등 즉시 활성화되는 사용자를 생성
     * @param nickname 닉네임
     * @param email 이메일
     * @return User (status: ACTIVE)
     */
    public User createActiveUser(String nickname, String email) {
        return new User(nickname, email, UserStatus.ACTIVE);
    }
}
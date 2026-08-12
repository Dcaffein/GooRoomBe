package com.example.DunbarHorizon.account.application.service;

import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.application.port.in.LoginUseCase;
import com.example.DunbarHorizon.account.application.port.in.SignupUseCase;
import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.exception.AlreadyRegisteredEmailException;
import com.example.DunbarHorizon.account.domain.exception.InvalidVerificationTokenException;
import com.example.DunbarHorizon.account.domain.repository.AuthRepository;
import com.example.DunbarHorizon.account.domain.repository.PendingSignupRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import com.example.DunbarHorizon.global.event.user.UserActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SignupService implements SignupUseCase {
    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final PasswordHasher passwordHasher;
    private final LoginUseCase loginUseCase;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 인증 링크를 클릭해 이메일 소유를 증명한 사람이 자격증명을 정하는 지점이다.
     * 이 메서드에 도달하기 전까지 {@code users}/{@code auths}에는 아무 행도 없다.
     */
    @Override
    public AuthTokenResult signup(String token, String password, String nickname) {
        // 조회와 삭제가 한 연산(GETDEL)이라 동시 요청이 같은 토큰으로 두 번 계정을 만들 수 없다.
        String email = pendingSignupRepository.consumeEmailByToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);

        // 접수 시점에는 미가입이었어도 링크를 여는 사이에 다른 경로(OAuth)로 가입될 수 있다.
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new AlreadyRegisteredEmailException(email);
        });

        // save()가 끝나야 id가 생긴다. 이벤트 발행이 그 뒤여야 하는 이유다.
        User user = userRepository.save(User.createActive(email, nickname));
        authRepository.save(Auth.createLocalAuth(user.getId(), passwordHasher.encode(password)));

        publishActivated(user);

        return loginUseCase.issueTokens(user);
    }

    @Override
    public User registerOAuthUser(String email, String nickname, AuthProvider provider, String providerId) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = userRepository.save(User.createActive(email, nickname));
                    publishActivated(newUser);
                    return newUser;
                });

        if (!authRepository.existsByUserIdAndProviderAndProviderId(user.getId(), provider, providerId)) {
            authRepository.save(Auth.createOAuth(user.getId(), provider, providerId));
        }

        return user;
    }

    /**
     * {@code UserOutboxEventListener}가 {@code BEFORE_COMMIT} 리스너다. 트랜잭션 밖에서 발행하면
     * 리스너가 아예 실행되지 않아 outbox 행이 생기지 않고, Neo4j {@code SocialUser} 노드도
     * 만들어지지 않는다. 가입은 성공하고 소셜 그래프만 비는 형태라 에러 없이 조용히 깨진다.
     */
    private void publishActivated(User user) {
        eventPublisher.publishEvent(
                new UserActivatedEvent(user.getId(), user.getNickname(), user.getProfileImage()));
    }
}

package com.example.DunbarHorizon.account.application.service;

import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.exception.AlreadyRegisteredEmailException;
import com.example.DunbarHorizon.account.domain.repository.AuthRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import com.example.DunbarHorizon.global.event.user.UserActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Service
@RequiredArgsConstructor
@Transactional
public class DevUserService {

    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;

    public User createDummyUser(String email, String nickname) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new AlreadyRegisteredEmailException(email);
        });

        User user = userRepository.save(User.createActive(email, nickname));

        Auth auth = Auth.createLocalAuth(user.getId(), DEFAULT_PASSWORD, passwordHasher);
        authRepository.save(auth);

        eventPublisher.publishEvent(new UserActivatedEvent(user.getId(), user.getNickname(), user.getProfileImage()));

        return user;
    }
}

package com.example.DunbarHorizon.account.application.service;

import com.example.DunbarHorizon.account.application.port.in.LoginUseCase;
import com.example.DunbarHorizon.account.domain.exception.RefreshTokenNotFoundException;
import com.example.DunbarHorizon.global.event.DeviceTokenDeregisteredEvent;
import com.example.DunbarHorizon.global.security.AuthPrincipal;
import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.application.port.out.AuthTokenProvider;
import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.domain.exception.InvalidCredentialsException;
import com.example.DunbarHorizon.account.domain.exception.TokenTheftDetectedException;
import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import com.example.DunbarHorizon.account.domain.RefreshToken;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.repository.AuthRepository;
import com.example.DunbarHorizon.account.domain.repository.RefreshTokenRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LoginService implements LoginUseCase {
    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenProvider authTokenProvider;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 실패 세 경우(미가입 이메일 / LOCAL 자격증명 없음 / 비밀번호 불일치)를 구분하지 않는다.
     * 응답이 갈리면 비밀번호를 몰라도 상태코드만으로 가입 여부를 판별할 수 있기 때문이다.
     * 사유는 로그로만 남긴다.
     */
    @Override
    @Transactional
    public AuthTokenResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[login] 미가입 이메일로 로그인 시도");
                    return new InvalidCredentialsException();
                });

        Auth localAuth = authRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> {
                    log.warn("[login] LOCAL 자격증명 없는 계정으로 로그인 시도. userId={}", user.getId());
                    return new InvalidCredentialsException();
                });

        if (!passwordHasher.matches(password, localAuth.hashedPassword())) {
            log.warn("[login] 비밀번호 불일치. userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    @Override
    public AuthTokenResult issueTokens(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getRole().getKey());
        String accessToken = authTokenProvider.createAccessToken(principal);
        String refreshTokenValue = authTokenProvider.createRefreshToken(principal);

        LocalDateTime expiryDate = authTokenProvider.getExpirationTime(refreshTokenValue);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenValue(refreshTokenValue)
                .expiryDate(expiryDate)
                .build();

        refreshTokenRepository.save(refreshToken);

        return new AuthTokenResult(accessToken, refreshTokenValue);
    }

    @Override
    public AuthTokenResult reissue(String oldRefreshTokenValue) {
        // 쿠키 부재는 토큰 파싱까지 가기 전에 걸러낸다. 그대로 넘기면 파싱 단계에서
        // InvalidTokenException으로 뭉뚱그려져 "만료"와 "애초에 로그인 상태가 아님"이 구분되지 않는다.
        if (oldRefreshTokenValue == null || oldRefreshTokenValue.isBlank()) {
            throw new RefreshTokenNotFoundException();
        }

        // 만료·위조는 여기서 ExpiredTokenException / InvalidTokenException(둘 다 401)으로 올라간다.
        AuthPrincipal authPrincipal = authTokenProvider.validateToken(oldRefreshTokenValue);

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenValue(oldRefreshTokenValue);

        if (tokenOpt.isEmpty()) {
            refreshTokenRepository.deleteAllByUserId(authPrincipal.id());
            throw new TokenTheftDetectedException();
        }

        RefreshToken refreshToken = tokenOpt.get();

        // DB 기준 만료 검증은 두지 않는다. expiryDate는 JWT의 exp에서 그대로 뽑은 값이므로
        // DB 기준으로 만료면 위 validateToken에서 반드시 먼저 걸린다.

        String newAccessToken = authTokenProvider.createAccessToken(authPrincipal);
        String newRefreshToken = authTokenProvider.createRefreshToken(authPrincipal);

        LocalDateTime newExpiryDate = authTokenProvider.getExpirationTime(newRefreshToken);

        refreshToken.rotateTokenValue(newRefreshToken, newExpiryDate);

        return new AuthTokenResult(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(String refreshTokenValue, String fcmToken) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.deleteByTokenValue(refreshTokenValue);
        }
        if (fcmToken != null && !fcmToken.isBlank()) {
            eventPublisher.publishEvent(new DeviceTokenDeregisteredEvent(fcmToken));
        }
    }

}

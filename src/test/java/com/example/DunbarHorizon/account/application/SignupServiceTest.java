package com.example.DunbarHorizon.account.application;

import com.example.DunbarHorizon.account.application.port.in.LoginUseCase;
import com.example.DunbarHorizon.account.application.port.out.PasswordHasher;
import com.example.DunbarHorizon.account.application.service.SignupService;
import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.UserStatus;
import com.example.DunbarHorizon.account.domain.exception.AlreadyRegisteredEmailException;
import com.example.DunbarHorizon.account.domain.exception.InvalidVerificationTokenException;
import com.example.DunbarHorizon.account.domain.repository.AuthRepository;
import com.example.DunbarHorizon.account.domain.repository.PendingSignupRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import com.example.DunbarHorizon.global.event.user.UserActivatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @InjectMocks private SignupService signupService;

    @Mock private UserRepository userRepository;
    @Mock private AuthRepository authRepository;
    @Mock private PendingSignupRepository pendingSignupRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private LoginUseCase loginUseCase;
    @Mock private ApplicationEventPublisher eventPublisher;

    private void givenUserIsAssignedId(long id) {
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", id);
            return u;
        });
    }

    // ───────────────────────────── signup() ─────────────────────────────

    @Test
    @DisplayName("유효한 토큰으로 가입하면 토큰이 가리키는 이메일로 계정과 LOCAL auth가 생성된다")
    void signup_유효한_토큰_계정_생성() {
        // given
        given(pendingSignupRepository.consumeEmailByToken("valid-token"))
                .willReturn(Optional.of("test@test.com"));
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.empty());
        givenUserIsAssignedId(1L);
        given(passwordHasher.encode("Pw12345!")).willReturn("encoded-pw");

        // when
        signupService.signup("valid-token", "Pw12345!", "tester");

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("test@test.com");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(authRepository).save(any(Auth.class));
        verify(loginUseCase).issueTokens(any(User.class));
    }

    @Test
    @DisplayName("계정 생성 후 UserActivatedEvent가 발행된다 - Neo4j SocialUser 동기화 경로")
    void signup_활성화_이벤트_발행() {
        // given
        given(pendingSignupRepository.consumeEmailByToken("valid-token"))
                .willReturn(Optional.of("test@test.com"));
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.empty());
        givenUserIsAssignedId(42L);
        given(passwordHasher.encode(any())).willReturn("encoded-pw");

        // when
        signupService.signup("valid-token", "Pw12345!", "tester");

        // then - 이벤트가 누락되면 가입은 되는데 소셜 그래프에 노드가 없는 계정이 만들어진다
        ArgumentCaptor<UserActivatedEvent> captor = ArgumentCaptor.forClass(UserActivatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(42L);
        assertThat(captor.getValue().nickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("만료되었거나 존재하지 않는 토큰으로 가입하면 계정을 만들지 않는다")
    void signup_유효하지_않은_토큰() {
        // given
        given(pendingSignupRepository.consumeEmailByToken("expired-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> signupService.signup("expired-token", "Pw12345!", "tester"))
                .isInstanceOf(InvalidVerificationTokenException.class);

        verify(userRepository, never()).save(any());
        verify(authRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 소비된 토큰은 재사용되지 않는다")
    void signup_토큰_재사용_불가() {
        // given - consume은 조회와 삭제가 한 연산이라 두 번째 호출은 비어 있다
        given(pendingSignupRepository.consumeEmailByToken("one-shot"))
                .willReturn(Optional.of("test@test.com"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.empty());
        givenUserIsAssignedId(1L);
        given(passwordHasher.encode(any())).willReturn("encoded-pw");

        // when
        signupService.signup("one-shot", "Pw12345!", "tester");

        // then
        assertThatThrownBy(() -> signupService.signup("one-shot", "OtherPw!1A", "attacker"))
                .isInstanceOf(InvalidVerificationTokenException.class);

        verify(userRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("접수 후 다른 경로로 같은 이메일이 가입되면 계정을 만들지 않는다")
    void signup_링크를_여는_사이_가입된_이메일() {
        // given
        given(pendingSignupRepository.consumeEmailByToken("valid-token"))
                .willReturn(Optional.of("test@test.com"));
        given(userRepository.findByEmail("test@test.com"))
                .willReturn(Optional.of(User.createActive("test@test.com", "already")));

        // when & then
        assertThatThrownBy(() -> signupService.signup("valid-token", "Pw12345!", "tester"))
                .isInstanceOf(AlreadyRegisteredEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("두 가입 시도는 서로의 자격증명을 오염시키지 않는다 - 계정 선점 탈취 회귀 방지")
    void signup_서로_다른_시도가_오염되지_않는다() {
        // given - 같은 이메일로 두 번 접수되어 토큰이 둘 살아 있는 상황.
        //         과거에는 두 번째 요청이 첫 요청의 비밀번호를 덮어써 탈취가 성립했다.
        given(pendingSignupRepository.consumeEmailByToken("token-A"))
                .willReturn(Optional.of("victim@test.com"));
        given(userRepository.findByEmail("victim@test.com")).willReturn(Optional.empty());
        givenUserIsAssignedId(1L);
        given(passwordHasher.encode("victimPw!1")).willReturn("encoded-victim-pw");

        // when - A 토큰의 요청자가 정한 비밀번호로 가입한다
        signupService.signup("token-A", "victimPw!1", "victim");

        // then - 저장된 자격증명은 A 요청자의 것이어야 한다
        ArgumentCaptor<Auth> authCaptor = ArgumentCaptor.forClass(Auth.class);
        verify(authRepository).save(authCaptor.capture());
        assertThat(authCaptor.getValue().getPassword()).isEqualTo("encoded-victim-pw");
        assertThat(authCaptor.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);

        // 덮어쓰기 경로가 아예 없으므로 다른 토큰의 요청은 별개 계정 생성 시도가 된다
        verify(userRepository, times(1)).save(any());
    }

    // ─────────────────────── registerOAuthUser() ────────────────────────

    @Test
    @DisplayName("신규 OAuth 유저는 ACTIVE로 생성되고 UserActivatedEvent가 발행된다")
    void registerOAuthUser_신규_유저() {
        // given
        String email = "oauth@test.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());
        givenUserIsAssignedId(1L);
        given(authRepository.existsByUserIdAndProviderAndProviderId(1L, AuthProvider.GOOGLE, "google-id"))
                .willReturn(false);

        // when
        User result = signupService.registerOAuthUser(email, "oauthUser", AuthProvider.GOOGLE, "google-id");

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(eventPublisher).publishEvent(any(UserActivatedEvent.class));
        verify(authRepository).save(any(Auth.class));
    }

    @Test
    @DisplayName("기존 유저에 같은 provider auth가 이미 있으면 auth를 저장하지 않는다")
    void registerOAuthUser_이미_연동됨() {
        // given
        String email = "active@test.com";
        User activeUser = User.createActive(email, "active");
        ReflectionTestUtils.setField(activeUser, "id", 1L);

        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeUser));
        given(authRepository.existsByUserIdAndProviderAndProviderId(1L, AuthProvider.GOOGLE, "google-id"))
                .willReturn(true);

        // when
        signupService.registerOAuthUser(email, "active", AuthProvider.GOOGLE, "google-id");

        // then
        verify(authRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("기존 유저에 새 provider가 연동되면 auth만 추가하고 이벤트는 발행하지 않는다")
    void registerOAuthUser_새_provider_연동() {
        // given
        String email = "active@test.com";
        User activeUser = User.createActive(email, "active");
        ReflectionTestUtils.setField(activeUser, "id", 1L);

        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeUser));
        given(authRepository.existsByUserIdAndProviderAndProviderId(1L, AuthProvider.GOOGLE, "google-id"))
                .willReturn(false);

        // when
        signupService.registerOAuthUser(email, "active", AuthProvider.GOOGLE, "google-id");

        // then - 계정이 새로 생긴 게 아니므로 활성화 이벤트를 다시 쏘면 안 된다
        verify(authRepository).save(any(Auth.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
}

package com.example.DunbarHorizon.account.application;

import com.example.DunbarHorizon.account.application.port.out.EmailPort;
import com.example.DunbarHorizon.account.application.service.VerificationService;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.exception.InvalidVerificationTokenException;
import com.example.DunbarHorizon.account.domain.repository.PendingSignupRepository;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @InjectMocks private VerificationService verificationService;

    @Mock private UserRepository userRepository;
    @Mock private PendingSignupRepository pendingSignupRepository;
    @Mock private EmailPort emailPort;

    @Test
    @DisplayName("미가입 이메일로 접수하면 토큰을 저장하고 가입 링크 메일을 보낸다")
    void requestVerification_미가입_이메일() {
        // given
        given(userRepository.findByEmail("new@test.com")).willReturn(Optional.empty());

        // when
        verificationService.requestVerification("new@test.com");

        // then
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(pendingSignupRepository).save(tokenCaptor.capture(), eq("new@test.com"));
        verify(emailPort).sendSignupVerificationEmail(eq("new@test.com"), eq(tokenCaptor.getValue()));
        verify(emailPort, never()).sendAlreadyRegisteredEmail(anyString());
    }

    @Test
    @DisplayName("이미 가입된 이메일로 접수하면 예외 없이 안내 메일만 보내고 토큰을 만들지 않는다")
    void requestVerification_기가입_이메일() {
        // given
        given(userRepository.findByEmail("taken@test.com"))
                .willReturn(Optional.of(User.createActive("taken@test.com", "existing")));

        // when & then - 호출자에게는 미가입과 구분되지 않아야 한다
        assertThatCode(() -> verificationService.requestVerification("taken@test.com"))
                .doesNotThrowAnyException();

        verify(emailPort).sendAlreadyRegisteredEmail("taken@test.com");
        verify(pendingSignupRepository, never()).save(any(), any());
        verify(emailPort, never()).sendSignupVerificationEmail(any(), any());
    }

    @Test
    @DisplayName("접수는 이메일 등록 여부와 무관하게 같은 결과를 돌려준다 - 계정 열거 차단")
    void requestVerification_등록_여부가_드러나지_않는다() {
        // given
        given(userRepository.findByEmail("new@test.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail("taken@test.com"))
                .willReturn(Optional.of(User.createActive("taken@test.com", "existing")));

        // when & then - 어느 쪽도 예외를 던지지 않으므로 응답만으로는 판별할 수 없다.
        //               구분은 수신자만 볼 수 있는 메일 내용으로만 이뤄진다.
        assertThatCode(() -> verificationService.requestVerification("new@test.com"))
                .doesNotThrowAnyException();
        assertThatCode(() -> verificationService.requestVerification("taken@test.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("접수할 때마다 새 토큰이 발급되며 기존 토큰을 무효화하지 않는다")
    void requestVerification_기존_토큰을_죽이지_않는다() {
        // given - 무효화하면 공격자가 반복 접수로 피해자의 링크를 계속 죽일 수 있다
        given(userRepository.findByEmail("new@test.com")).willReturn(Optional.empty());

        // when
        verificationService.requestVerification("new@test.com");
        verificationService.requestVerification("new@test.com");

        // then
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(pendingSignupRepository, org.mockito.Mockito.times(2))
                .save(tokenCaptor.capture(), eq("new@test.com"));
        assertThat(tokenCaptor.getAllValues().get(0)).isNotEqualTo(tokenCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("유효한 토큰을 조회하면 대상 이메일을 돌려주고 토큰을 소비하지 않는다")
    void resolveEmail_유효한_토큰() {
        // given
        given(pendingSignupRepository.findEmailByToken("valid-token"))
                .willReturn(Optional.of("test@test.com"));

        // when
        String email = verificationService.resolveEmail("valid-token");

        // then - 폼을 그리기 전 확인이므로 여기서 토큰이 소비되면 안 된다
        assertThat(email).isEqualTo("test@test.com");
        verify(pendingSignupRepository, never()).consumeEmailByToken(any());
    }

    @Test
    @DisplayName("만료되었거나 존재하지 않는 토큰을 조회하면 예외를 던진다")
    void resolveEmail_유효하지_않은_토큰() {
        // given
        given(pendingSignupRepository.findEmailByToken(anyString())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> verificationService.resolveEmail("expired-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }
}

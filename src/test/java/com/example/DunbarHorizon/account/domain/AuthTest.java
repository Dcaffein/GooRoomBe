package com.example.DunbarHorizon.account.domain;

import com.example.DunbarHorizon.account.domain.exception.InvalidPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTest {

    /** 인코딩을 흉내만 내는 대역. 정책 검증이 인코딩보다 먼저인지 보기 위해 호출을 기록한다. */
    private static class FakeCipher implements PasswordCipher {
        boolean encodeCalled = false;

        @Override
        public String encode(String rawPassword) {
            encodeCalled = true;
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    }

    @Test
    @DisplayName("정책을 만족하는 비밀번호로 로컬 자격증명을 만든다")
    void createLocalAuth_EncodesValidPassword() {
        // given
        FakeCipher cipher = new FakeCipher();

        // when
        Auth auth = Auth.createLocalAuth(1L, "String123!", cipher);

        // then - 원문이 아니라 인코딩된 값이 담긴다
        assertThat(auth.getPassword()).isEqualTo("encoded:String123!");
        assertThat(auth.getProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    @DisplayName("정책을 어긴 비밀번호는 인코딩 전에 거부한다")
    void createLocalAuth_RejectsWeakPasswordBeforeEncoding() {
        // given - 8자 미만. 웹 DTO를 거치지 않는 진입로에서도 규칙을 우회할 수 없어야 한다
        FakeCipher cipher = new FakeCipher();

        // when & then
        assertThatThrownBy(() -> Auth.createLocalAuth(1L, "pw123!A", cipher))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage(Auth.PASSWORD_MESSAGE);
        assertThat(cipher.encodeCalled).isFalse();
    }

    @Test
    @DisplayName("특수문자가 없으면 거부한다")
    void createLocalAuth_RequiresSpecialCharacter() {
        assertThatThrownBy(() -> Auth.createLocalAuth(1L, "String1234", new FakeCipher()))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName("null 비밀번호도 거부한다")
    void createLocalAuth_RejectsNull() {
        assertThatThrownBy(() -> Auth.createLocalAuth(1L, null, new FakeCipher()))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName("대조는 엔티티 안에서 이뤄지고 해시를 밖으로 내보내지 않는다")
    void matches_ComparesInsideEntity() {
        // given
        FakeCipher cipher = new FakeCipher();
        Auth auth = Auth.createLocalAuth(1L, "String123!", cipher);

        // when & then
        assertThat(auth.matches("String123!", cipher)).isTrue();
        assertThat(auth.matches("Wrong123!", cipher)).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 없는 OAuth 자격증명은 어떤 값과도 일치하지 않는다")
    void matches_OAuthAuthNeverMatches() {
        // given - password 컬럼이 null이라 그대로 비교하면 NPE가 난다
        Auth oauth = Auth.createOAuth(1L, AuthProvider.GOOGLE, "google-sub");

        // when & then
        assertThatCode(() -> assertThat(oauth.matches("anything", new FakeCipher())).isFalse())
                .doesNotThrowAnyException();
    }
}

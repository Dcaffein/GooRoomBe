package com.example.DunbarHorizon.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTest {

    @Test
    @DisplayName("로컬 자격증명은 해싱된 값만 받는다")
    void createLocalAuth_StoresHashedValue() {
        // given
        HashedPassword hashed = new HashedPassword("$2a$10$encoded");

        // when
        Auth auth = Auth.createLocalAuth(1L, hashed);

        // then
        assertThat(auth.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(auth.hashedPassword()).isEqualTo(hashed);
    }

    @Test
    @DisplayName("빈 해시는 만들 수 없다")
    void hashedPassword_RejectsBlank() {
        assertThatThrownBy(() -> new HashedPassword("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashedPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OAuth 자격증명은 비밀번호가 없다")
    void createOAuth_HasNoPassword() {
        // given & when
        Auth oauth = Auth.createOAuth(1L, AuthProvider.GOOGLE, "google-sub");

        // then - 호출자가 null을 다루도록 원시 게터 대신 이 메서드만 노출된다
        assertThat(oauth.hashedPassword()).isNull();
        assertThat(oauth.getProviderId()).isEqualTo("google-sub");
    }
}

package com.example.DunbarHorizon.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Nested
    @DisplayName("이메일 동일성")
    class EmailIdentity {

        @Test
        @DisplayName("대소문자를 소문자로 맞춘다")
        void normalizeEmail_Lowercases() {
            assertThat(User.normalizeEmail("Kim@Example.COM")).isEqualTo("kim@example.com");
        }

        @Test
        @DisplayName("앞뒤 공백을 걷어낸다")
        void normalizeEmail_Trims() {
            assertThat(User.normalizeEmail("  kim@example.com  ")).isEqualTo("kim@example.com");
        }

        @Test
        @DisplayName("서버 로케일이 바뀌어도 같은 결과를 낸다")
        void normalizeEmail_IsLocaleIndependent() {
            // given - 터키어 로케일은 I를 점 없는 ı로 내린다. 신원이 로케일에 갈리면 안 된다
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(new Locale("tr", "TR"));

                // when & then
                assertThat(User.normalizeEmail("KIM@EXAMPLE.COM")).isEqualTo("kim@example.com");
            } finally {
                Locale.setDefault(original);
            }
        }

        @Test
        @DisplayName("공급자별 별칭 규칙은 적용하지 않는다")
        void normalizeEmail_KeepsProviderSpecificForms() {
            // given - gmail의 . 무시나 + 별칭을 흉내 내면 다른 사람의 계정이 합쳐질 수 있다
            assertThat(User.normalizeEmail("k.i.m+tag@gmail.com")).isEqualTo("k.i.m+tag@gmail.com");
        }

        @Test
        @DisplayName("계정 생성 시 정규화된 이메일이 저장된다")
        void createActive_NormalizesEmail() {
            assertThat(User.createActive("  Kim@Example.COM ", "tester").getEmail())
                    .isEqualTo("kim@example.com");
        }
    }

    @Nested
    @DisplayName("닉네임")
    class Nickname {

        @Test
        @DisplayName("최대 길이를 넘으면 거부하지 않고 잘라낸다")
        void createActive_TruncatesLongNickname() {
            // given - 구글 표시 이름은 사용자가 고칠 수 없어 거부하면 로그인할 방법이 없다.
            //         계정 생성이 웹 DTO를 거치지 않는 경로(OAuth)에서도 길이가 지켜져야 한다
            User user = User.createActive("long@test.com", "Christopher Alexander Thompson");

            // then - 컬럼 길이에 걸려 가입이 실패하지 않는다
            assertThat(user.getNickname()).hasSize(User.NICKNAME_MAX_LENGTH);
            assertThat(user.getNickname()).isEqualTo("Christopher Alexande");
        }

        @Test
        @DisplayName("앞뒤 공백을 걷어낸다")
        void createActive_TrimsNickname() {
            assertThat(User.createActive("u@test.com", "  이수환  ").getNickname()).isEqualTo("이수환");
        }

        @Test
        @DisplayName("최대 길이 이하는 그대로 둔다")
        void createActive_KeepsShortNickname() {
            assertThat(User.createActive("u@test.com", "이수환").getNickname()).isEqualTo("이수환");
        }

        @Test
        @DisplayName("프로필 수정도 같은 규칙을 통과한다")
        void updateProfile_AppliesSameRule() {
            // given
            User user = User.createActive("u@test.com", "before");

            // when
            user.updateProfile("Christopher Alexander Thompson", "key");

            // then
            assertThat(user.getNickname()).hasSize(User.NICKNAME_MAX_LENGTH);
        }
    }
}

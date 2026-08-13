package com.example.DunbarHorizon.account.domain.policy;

import com.example.DunbarHorizon.account.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPolicyTest {

    @Test
    @DisplayName("대소문자를 소문자로 맞춘다")
    void normalize_LowercasesEmail() {
        assertThat(EmailPolicy.normalize("Kim@Example.COM")).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("앞뒤 공백을 걷어낸다")
    void normalize_TrimsWhitespace() {
        assertThat(EmailPolicy.normalize("  kim@example.com  ")).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("서버 로케일이 바뀌어도 같은 결과를 낸다")
    void normalize_IsLocaleIndependent() {
        // given - 터키어 로케일은 I를 점 없는 ı로 내린다. 신원 판단이 로케일에 갈리면 안 된다
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            // when & then
            assertThat(EmailPolicy.normalize("KIM@EXAMPLE.COM")).isEqualTo("kim@example.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("null은 그대로 통과시킨다")
    void normalize_PassesNullThrough() {
        assertThat(EmailPolicy.normalize(null)).isNull();
    }

    @Test
    @DisplayName("계정 생성 시 정규화된 이메일이 저장된다")
    void createActive_NormalizesEmail() {
        // given - 신원 앵커가 DB 콜레이션이 아니라 코드로 정해져야 한다
        User user = User.createActive("  Kim@Example.COM ", "tester");

        // then
        assertThat(user.getEmail()).isEqualTo("kim@example.com");
    }

    @Test
    @DisplayName("공급자별 별칭 규칙은 적용하지 않는다")
    void normalize_DoesNotApplyProviderSpecificRules() {
        // given - gmail의 . 무시나 + 별칭을 흉내 내면 서로 다른 사람의 계정이 합쳐질 수 있다
        assertThat(EmailPolicy.normalize("k.i.m+tag@gmail.com")).isEqualTo("k.i.m+tag@gmail.com");
    }
}

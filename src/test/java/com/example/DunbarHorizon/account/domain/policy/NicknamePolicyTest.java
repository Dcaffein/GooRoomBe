package com.example.DunbarHorizon.account.domain.policy;

import com.example.DunbarHorizon.account.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknamePolicyTest {

    @Test
    @DisplayName("최대 길이를 넘는 닉네임은 잘라낸다")
    void normalize_TruncatesOverMaxLength() {
        // given - 구글 표시 이름은 사용자가 고칠 수 없어 거부하면 로그인할 방법이 없다
        String tooLong = "Christopher Alexander Thompson";

        // when
        String normalized = NicknamePolicy.normalize(tooLong);

        // then
        assertThat(normalized).hasSize(NicknamePolicy.MAX_LENGTH);
        assertThat(normalized).isEqualTo("Christopher Alexande");
    }

    @Test
    @DisplayName("앞뒤 공백을 걷어낸다")
    void normalize_TrimsWhitespace() {
        assertThat(NicknamePolicy.normalize("  이수환  ")).isEqualTo("이수환");
    }

    @Test
    @DisplayName("최대 길이 이하는 그대로 둔다")
    void normalize_KeepsShortNickname() {
        assertThat(NicknamePolicy.normalize("이수환")).isEqualTo("이수환");
    }

    @Test
    @DisplayName("null은 그대로 통과시킨다")
    void normalize_PassesNullThrough() {
        assertThat(NicknamePolicy.normalize(null)).isNull();
    }

    @Test
    @DisplayName("계정 생성이 DTO를 거치지 않는 경로에서도 길이를 지킨다")
    void createActive_AppliesPolicy() {
        // given - OAuth 가입은 두 웹 DTO를 모두 우회한다
        String googleName = "Christopher Alexander Thompson";

        // when
        User user = User.createActive("long@test.com", googleName);

        // then - 컬럼 길이에 걸려 가입이 실패하지 않는다
        assertThat(user.getNickname()).hasSize(NicknamePolicy.MAX_LENGTH);
    }

    @Test
    @DisplayName("프로필 수정도 같은 규칙을 통과한다")
    void updateProfile_AppliesPolicy() {
        // given
        User user = User.createActive("u@test.com", "before");

        // when
        user.updateProfile("Christopher Alexander Thompson", "key");

        // then
        assertThat(user.getNickname()).hasSize(NicknamePolicy.MAX_LENGTH);
    }
}

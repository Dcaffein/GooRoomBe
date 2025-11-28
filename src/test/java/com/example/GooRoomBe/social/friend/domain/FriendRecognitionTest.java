package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FriendRecognitionTest {

    @Mock
    private SocialUser user;

    @Test
    @DisplayName("생성 시 기본값(alias 빈 문자열, onIntroduce false)이 설정된다")
    void constructor_DefaultValues() {
        FriendRecognition recognition = new FriendRecognition(user);

        assertThat(recognition.getUser()).isEqualTo(user);
        assertThat(recognition.getFriendAlias()).isEmpty();
        assertThat(recognition.isOnIntroduce()).isFalse();
    }

    @Test
    @DisplayName("별명 수정이 정상적으로 동작한다")
    void updateFriendAlias() {
        FriendRecognition recognition = new FriendRecognition(user);
        recognition.updateFriendAlias("New Alias");

        assertThat(recognition.getFriendAlias()).isEqualTo("New Alias");
    }

    @Test
    @DisplayName("소개 여부 수정이 정상적으로 동작한다")
    void updateOnIntroduce() {
        FriendRecognition recognition = new FriendRecognition(user);
        recognition.updateOnIntroduce(true);

        assertThat(recognition.isOnIntroduce()).isTrue();
    }
}
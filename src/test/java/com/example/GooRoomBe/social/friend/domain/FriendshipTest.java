package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.friend.exception.FriendshipAuthorizationException;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipTest {

    @Mock
    private SocialUser userA;
    @Mock
    private SocialUser userB;
    @Mock
    private SocialUser stranger;

    private Friendship friendship;

    @BeforeEach
    void setUp() {
        when(userA.getId()).thenReturn("user-2");
        when(userB.getId()).thenReturn("user-1");

        friendship = new Friendship(userA, userB);
    }

    @Test
    @DisplayName("생성 시 두 유저의 ID를 정렬하여 Composite ID를 만든다")
    void constructor_ShouldGenerateSortedCompositeId() {
        // user-1이 user-2보다 사전순으로 앞서므로 "user-1_user-2"가 되어야 함
        assertThat(friendship.getId()).isEqualTo("user-1_user-2");

        // 생성일자가 설정되어야 함
        assertThat(friendship.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getFriend: 내 ID를 주면 상대방(친구) 유저 객체를 반환한다")
    void getFriend_ShouldReturnTheOtherUser() {
        // When: userA(나) 기준으로 친구를 요청하면
        SocialUser friendOfA = friendship.getFriend(userA.getId());

        // Then: userB가 나와야 한다
        assertThat(friendOfA).isEqualTo(userB);

        // 반대로 userB 기준으로 요청하면 userA가 나와야 한다
        assertThat(friendship.getFriend(userB.getId())).isEqualTo(userA);
    }

    @Test
    @DisplayName("updateAlias: 내 인식(Recognition)의 별명만 수정되어야 한다")
    void updateAlias_ShouldUpdateFriendOnlyMyRecognition() {
        // Given
        String newAlias = "Best Friend";

        // When: userA가 별명을 수정함
        friendship.updateFriendAlias(userA.getId(), newAlias);

        // Then
        // 1. userA가 설정한 별명은 바뀌어야 함
        assertThat(friendship.getFriendAlias(userA.getId())).isEqualTo(newAlias);

        // 2. userB의 별명은 초기값(null -> "") 그대로여야 함 (영향 없음)
        assertThat(friendship.getFriendAlias(userB.getId())).isEmpty();
    }

    @Test
    @DisplayName("checkDeletable: 참여자는 삭제 권한이 있고, 제3자는 예외가 발생한다")
    void checkDeletable_AuthorizationTest() {
        // 친구 userA는 통과
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> friendship.checkDeletable(userA.getId())
        );

        // 친구가 아닌 stranger는 예외
        when(stranger.getId()).thenReturn("user-stranger");

        assertThatThrownBy(() -> friendship.checkDeletable(stranger.getId()))
                .isInstanceOf(FriendshipAuthorizationException.class);
    }

    @Test
    @DisplayName("관계에 없는 유저 ID로 조회 시 예외가 발생한다")
    void invalidUser_ShouldThrowException() {
        String invalidId = "unknown-user";

        assertThatThrownBy(() -> friendship.getFriend(invalidId))
                .isInstanceOf(FriendshipNotFoundException.class);

        assertThatThrownBy(() -> friendship.updateFriendAlias(invalidId, "alias"))
                .isInstanceOf(FriendshipNotFoundException.class);
    }
}
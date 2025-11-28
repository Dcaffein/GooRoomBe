package com.example.GooRoomBe.social.friend.infrastructure;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendshipAdapterTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendshipAdapter friendshipAdapter;

    @Mock
    private Friendship friendship;

    private final String MY_ID = "userA";
    private final String FRIEND_ID = "userB";

    @Test
    @DisplayName("getFriendship: 친구 관계가 존재하면 Friendship 객체를 반환한다")
    void getFriendship_Success() {
        // given
        given(friendshipRepository.findFriendshipByUsers(MY_ID, FRIEND_ID))
                .willReturn(Optional.of(friendship));

        // when
        Friendship result = friendshipAdapter.getFriendship(MY_ID, FRIEND_ID);

        // then
        assertThat(result).isEqualTo(friendship);
        // 레포지토리의 올바른 메서드가 호출되었는지 검증
        verify(friendshipRepository).findFriendshipByUsers(MY_ID, FRIEND_ID);
    }

    @Test
    @DisplayName("getFriendship: 친구 관계가 없으면 FriendshipNotFoundException을 던진다")
    void getFriendship_Fail_NotFound() {
        // given
        given(friendshipRepository.findFriendshipByUsers(MY_ID, FRIEND_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> friendshipAdapter.getFriendship(MY_ID, FRIEND_ID))
                .isInstanceOf(FriendshipNotFoundException.class)
                .hasMessageContaining(MY_ID);
    }

    @Test
    @DisplayName("save: 레포지토리의 save를 호출하고 결과를 반환한다")
    void save_Delegation() {
        // given
        given(friendshipRepository.save(friendship)).willReturn(friendship);

        // when
        Friendship result = friendshipAdapter.save(friendship);

        // then
        assertThat(result).isEqualTo(friendship);
        verify(friendshipRepository).save(friendship);
    }

    @Test
    @DisplayName("delete: 레포지토리의 delete를 호출한다")
    void delete_Delegation() {
        // when
        friendshipAdapter.delete(friendship);

        // then
        verify(friendshipRepository).delete(friendship);
    }
}
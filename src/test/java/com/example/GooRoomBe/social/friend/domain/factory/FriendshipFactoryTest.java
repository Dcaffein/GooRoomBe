package com.example.GooRoomBe.social.friend.domain.factory;

import com.example.GooRoomBe.social.friend.infrastructure.FriendshipPort;
import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.AlreadyFriendException;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotAcceptedException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FriendshipFactoryTest {

    @Mock
    private FriendshipPort friendshipPort;

    @InjectMocks
    private FriendshipFactory friendshipFactory;

    @Test
    @DisplayName("수락된 요청이고 기존 친구가 아니라면 Friendship을 생성한다")
    void createFromRequest_Success() {
        // given
        FriendRequest request = mock(FriendRequest.class);
        SocialUser requester = mock(SocialUser.class);
        SocialUser receiver = mock(SocialUser.class);

        // Mock 동작 정의
        given(request.isAccepted()).willReturn(true);
        given(request.getRequester()).willReturn(requester);
        given(request.getReceiver()).willReturn(receiver);

        // ID 조회 시 리턴값 설정 (이미 친구인지 확인 로직용)
        given(requester.getId()).willReturn("userA");
        given(receiver.getId()).willReturn("userB");

        given(friendshipPort.existsFriendshipBetween("userA", "userB")).willReturn(false);

        // when
        Friendship friendship = friendshipFactory.createFromRequest(request);

        // then
        assertThat(friendship).isNotNull();
        // Friendship 내부 로직 검증은 FriendshipTest에서 했으므로 여기선 생성 여부만 확인
    }

    @Test
    @DisplayName("수락되지 않은 요청으로 생성 시도 시 예외가 발생한다")
    void createFromRequest_Fail_NotAccepted() {
        // given
        FriendRequest request = mock(FriendRequest.class);
        given(request.isAccepted()).willReturn(false); // 수락 안됨

        // when & then
        assertThatThrownBy(() -> friendshipFactory.createFromRequest(request))
                .isInstanceOf(FriendRequestNotAcceptedException.class);
    }

    @Test
    @DisplayName("이미 친구 관계가 존재하면 예외가 발생한다")
    void createFromRequest_Fail_AlreadyFriend() {
        // given
        FriendRequest request = mock(FriendRequest.class);
        SocialUser requester = mock(SocialUser.class);
        SocialUser receiver = mock(SocialUser.class);

        given(request.isAccepted()).willReturn(true);
        given(request.getRequester()).willReturn(requester);
        given(request.getReceiver()).willReturn(receiver);
        given(requester.getId()).willReturn("userA");
        given(receiver.getId()).willReturn("userB");

        // 이미 친구임
        given(friendshipPort.existsFriendshipBetween("userA", "userB")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> friendshipFactory.createFromRequest(request))
                .isInstanceOf(AlreadyFriendException.class);
    }
}
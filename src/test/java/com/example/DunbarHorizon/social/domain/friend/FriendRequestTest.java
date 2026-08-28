package com.example.DunbarHorizon.social.domain.friend;

import com.example.DunbarHorizon.social.domain.friend.exception.CannotRequestToSelfException;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestAuthorizationException;
import com.example.DunbarHorizon.social.domain.friend.exception.FriendRequestInvalidException;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FriendRequestTest {

    private SocialUser requester;
    private SocialUser receiver;

    @BeforeEach
    void setUp() {
        requester = new SocialUser(1L, "요청자", "");
        receiver = new SocialUser(2L, "수신자", "");
    }

    @Test
    @DisplayName("자기 자신에게 친구 요청을 보내면 예외가 발생한다")
    void constructor_SelfRequest_Fail() {
        // when & then
        assertThatThrownBy(() -> new FriendRequest(requester, requester))
                .isInstanceOf(CannotRequestToSelfException.class);
    }

    @Test
    @DisplayName("수신자가 친구 요청을 수락하면 상태가 ACCEPTED가 된다")
    void accept_Success() {
        // given
        FriendRequest friendRequest = new FriendRequest(requester, receiver);

        // when
        friendRequest.updateStatus(2L, FriendRequestStatus.ACCEPTED);

        // then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("요청자가 수락을 시도하면 예외가 발생한다")
    void accept_ByRequester_Fail() {
        // given
        FriendRequest request = new FriendRequest(requester, receiver);

        // when & then
        assertThatThrownBy(() -> request.updateStatus(1L, FriendRequestStatus.ACCEPTED))
                .isInstanceOf(FriendRequestAuthorizationException.class);
    }

    @Test
    void updateStatus_ToHidden_Success() {
        FriendRequest request = new FriendRequest(requester, receiver);

        request.updateStatus(receiver.getId(), FriendRequestStatus.HIDDEN);

        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.HIDDEN);
    }

    @Test
    void updateStatus_ToPending_FromHidden_Success() {
        FriendRequest request = new FriendRequest(requester, receiver);
        request.updateStatus(receiver.getId(), FriendRequestStatus.HIDDEN);

        request.updateStatus(receiver.getId(), FriendRequestStatus.PENDING);

        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
    }

    @Test
    void updateStatus_WithoutTargetStatus_Fail() {
        FriendRequest request = new FriendRequest(requester, receiver);

        assertThatThrownBy(() -> request.updateStatus(receiver.getId(), null))
                .isInstanceOf(FriendRequestInvalidException.class);
    }

    @Test
    @DisplayName("요청자가 PENDING 상태의 요청을 취소하면 예외가 발생하지 않는다")
    void cancel_ByRequester_Success() {
        // given
        FriendRequest request = new FriendRequest(requester, receiver);

        // when & then
        assertThatNoException().isThrownBy(() -> request.cancel(1L));
    }

    @Test
    @DisplayName("수신자가 자신이 받은 요청을 취소하려 하면 예외가 발생한다")
    void cancel_ByReceiver_Fail() {
        // given
        FriendRequest request = new FriendRequest(requester, receiver);

        // when & then
        assertThatThrownBy(() -> request.cancel(2L))
                .isInstanceOf(FriendRequestAuthorizationException.class);
    }

    @Test
    @DisplayName("이미 수락된 요청을 취소하려 하면 예외가 발생한다")
    void cancel_AfterAccepted_Fail() {
        // given
        FriendRequest request = new FriendRequest(requester, receiver);
        ReflectionTestUtils.setField(request, "status", FriendRequestStatus.ACCEPTED);

        // when & then
        assertThatThrownBy(() -> request.cancel(1L))
                .isInstanceOf(FriendRequestInvalidException.class);
    }

    @Test
    @DisplayName("HIDDEN 상태의 요청을 취소하려 하면 예외가 발생한다")
    void cancel_WhenHidden_Fail() {
        // given
        FriendRequest request = new FriendRequest(requester, receiver);
        ReflectionTestUtils.setField(request, "status", FriendRequestStatus.HIDDEN);

        // when & then
        assertThatThrownBy(() -> request.cancel(1L))
                .isInstanceOf(FriendRequestInvalidException.class);
    }
}

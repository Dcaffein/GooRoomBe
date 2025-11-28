package com.example.GooRoomBe.social.friend.domain;

import com.example.GooRoomBe.social.friend.domain.event.FriendRequestAcceptedEvent;
import com.example.GooRoomBe.social.friend.exception.FriendRequestAuthorizationException;
import com.example.GooRoomBe.social.friend.exception.FriendRequestNotPendingException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FriendRequestTest {

    @Mock
    private SocialUser requester;
    @Mock
    private SocialUser receiver;
    @Mock
    private SocialUser stranger;

    private FriendRequest friendRequest;

    private final String REQUESTER_ID = "user-A";
    private final String RECEIVER_ID = "user-B";
    private final String STRANGER_ID = "user-C";

    @BeforeEach
    void setUp() {
        // Mock ID 설정
        lenient().when(requester.getId()).thenReturn(REQUESTER_ID);
        lenient().when(receiver.getId()).thenReturn(RECEIVER_ID);

        // FriendRequest 생성 (초기 상태: PENDING)
        friendRequest = new FriendRequest(requester, receiver);
    }

    // ==========================================
    // 1. 수락 (Accept) 테스트
    // ==========================================

    @Test
    @DisplayName("수신자(Receiver)는 친구 요청을 수락할 수 있으며, 수락 시 이벤트가 발행된다")
    void accept_Success_WithEvent() {
        // When: 수신자가 수락 요청
        friendRequest.updateStatus(FriendRequestStatus.ACCEPTED, RECEIVER_ID);

        // Then 1: 상태 변경 확인
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(friendRequest.isAccepted()).isTrue();

        // Then 2: 도메인 이벤트 발행 확인 (AbstractAggregateRoot 기능)
        Collection<Object> events = ReflectionTestUtils.invokeMethod(friendRequest, "domainEvents");
        assertThat(events).hasSize(1);

        Object event = events.iterator().next();
        assertThat(event).isInstanceOf(FriendRequestAcceptedEvent.class);

        FriendRequestAcceptedEvent acceptedEvent = (FriendRequestAcceptedEvent) event;
        assertThat(acceptedEvent.requesterId()).isEqualTo(REQUESTER_ID);
        assertThat(acceptedEvent.receiverId()).isEqualTo(RECEIVER_ID);
    }

    @Test
    @DisplayName("요청자(Requester)나 제3자는 수락할 수 없다 (권한 예외)")
    void accept_Fail_Unauthorized() {
        // When & Then: 요청자가 수락 시도 -> 예외
        assertThatThrownBy(() -> friendRequest.updateStatus(FriendRequestStatus.ACCEPTED, REQUESTER_ID))
                .isInstanceOf(FriendRequestAuthorizationException.class)
                .hasMessageContaining(REQUESTER_ID); // 에러 메시지에 시도한 ID가 포함되는지

        // When & Then: 제3자가 수락 시도 -> 예외
        assertThatThrownBy(() -> friendRequest.updateStatus(FriendRequestStatus.ACCEPTED, STRANGER_ID))
                .isInstanceOf(FriendRequestAuthorizationException.class);
    }

    @Test
    @DisplayName("수신자는 요청을 숨김(HIDDEN) 처리할 수 있다")
    void hide_Success() {
        // When
        friendRequest.updateStatus(FriendRequestStatus.HIDDEN, RECEIVER_ID);

        // Then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.HIDDEN);
    }

    @Test
    @DisplayName("숨김 상태에서 다시 PENDING으로 복구할 수 있다")
    void restore_FromHidden_ToPending() {
        // Given: 이미 숨김 상태
        friendRequest.updateStatus(FriendRequestStatus.HIDDEN, RECEIVER_ID);

        // When: 다시 PENDING으로 변경
        friendRequest.updateStatus(FriendRequestStatus.PENDING, RECEIVER_ID);

        // Then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
    }

    // ==========================================
    // 3. 취소 (Cancel) 테스트
    // ==========================================

    @Test
    @DisplayName("요청자(Requester)는 PENDING 상태인 요청을 취소할 수 있다")
    void cancel_Success() {
        // When & Then: 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> friendRequest.checkCancelable(REQUESTER_ID));
    }

    @Test
    @DisplayName("수신자나 제3자는 요청을 취소할 수 없다")
    void cancel_Fail_Unauthorized() {
        // 수신자가 취소 시도
        assertThatThrownBy(() -> friendRequest.checkCancelable(RECEIVER_ID))
                .isInstanceOf(FriendRequestAuthorizationException.class);

        // 제3자가 취소 시도
        assertThatThrownBy(() -> friendRequest.checkCancelable(STRANGER_ID))
                .isInstanceOf(FriendRequestAuthorizationException.class);
    }

    @Test
    @DisplayName("PENDING 상태가 아니면(예: 이미 수락됨) 취소할 수 없다")
    void cancel_Fail_NotPending() {
        // Given: 이미 수락된 상태
        friendRequest.updateStatus(FriendRequestStatus.ACCEPTED, RECEIVER_ID);

        // When & Then: 요청자가 취소 시도 -> 상태 오류
        assertThatThrownBy(() -> friendRequest.checkCancelable(REQUESTER_ID))
                .isInstanceOf(FriendRequestNotPendingException.class);
    }
}
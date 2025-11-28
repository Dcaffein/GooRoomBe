package com.example.GooRoomBe.social.friend.domain.service;

import com.example.GooRoomBe.social.friend.exception.AlreadyFriendException;
import com.example.GooRoomBe.social.friend.exception.DuplicateFriendRequestException;
import com.example.GooRoomBe.social.friend.infrastructure.FriendRequestRepository;
import com.example.GooRoomBe.social.friend.infrastructure.FriendshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FriendRequestDuplicationValidatorTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendRequestDuplicationValidator validator;

    private final String requesterId = "userA";
    private final String receiverId = "userB";

    @Test
    @DisplayName("중복 요청도 없고 이미 친구도 아니면 검증을 통과한다")
    void validateNewRequest_Success() {
        // given
        given(friendRequestRepository.existsRequestBetween(requesterId, receiverId)).willReturn(false);
        given(friendshipRepository.existsFriendshipBetween(requesterId, receiverId)).willReturn(false);

        // when & then
        assertDoesNotThrow(() -> validator.validateNewRequest(requesterId, receiverId));
    }

    @Test
    @DisplayName("이미 친구 요청이 존재하면 예외가 발생한다")
    void validateNewRequest_Fail_DuplicateRequest() {
        // given
        given(friendRequestRepository.existsRequestBetween(requesterId, receiverId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.validateNewRequest(requesterId, receiverId))
                .isInstanceOf(DuplicateFriendRequestException.class);
    }

    @Test
    @DisplayName("이미 친구 관계라면 예외가 발생한다")
    void validateNewRequest_Fail_AlreadyFriend() {
        // given
        given(friendRequestRepository.existsRequestBetween(requesterId, receiverId)).willReturn(false);
        given(friendshipRepository.existsFriendshipBetween(requesterId, receiverId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.validateNewRequest(requesterId, receiverId))
                .isInstanceOf(AlreadyFriendException.class);
    }
}
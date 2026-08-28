package com.example.DunbarHorizon.social.application;

import com.example.DunbarHorizon.social.application.service.FriendRequestRequesterActionService;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import com.example.DunbarHorizon.social.domain.friend.FriendTestFactory;
import com.example.DunbarHorizon.social.domain.friend.exception.DuplicateFriendRequestException;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendRequestRepository;
import com.example.DunbarHorizon.social.domain.friend.FriendshipBroker;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import com.example.DunbarHorizon.social.domain.socialUser.repository.SocialUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendRequestRequesterActionServiceTest {

    @InjectMocks
    private FriendRequestRequesterActionService requesterService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private SocialUserRepository socialUserRepository;

    @Mock
    private FriendshipBroker friendshipBroker;

    @Test
    @DisplayName("신청자가 친구 요청을 생성하면 유저 존재 확인 후 Broker를 통해 요청을 저장한다")
    void createFriendRequest_Success() {
        // given
        Long reqId = 1L;
        Long resId = 2L;
        SocialUser requester = mock(SocialUser.class);
        SocialUser receiver = mock(SocialUser.class);
        FriendRequest mockRequest = mock(FriendRequest.class);

        given(socialUserRepository.findById(reqId)).willReturn(Optional.of(requester));
        given(socialUserRepository.findById(resId)).willReturn(Optional.of(receiver));
        given(friendshipBroker.propose(requester, receiver)).willReturn(mockRequest);

        // when
        requesterService.sendRequest(reqId, resId);

        // then
        verify(friendRequestRepository).saveRequest(mockRequest);
    }

    @Test
    @DisplayName("친구 요청 전송 시 저장 전 객체를 반환해 requester/receiver가 null이 아님을 보장한다")
    void sendRequest_ReturnsPreSaveObject_WithPopulatedRelationships() {
        // given
        Long reqId = 1L;
        Long resId = 2L;
        SocialUser requester = mock(SocialUser.class);
        SocialUser receiver = mock(SocialUser.class);
        FriendRequest mockRequest = mock(FriendRequest.class);
        FriendRequest savedWithNullRelations = mock(FriendRequest.class);

        given(socialUserRepository.findById(reqId)).willReturn(Optional.of(requester));
        given(socialUserRepository.findById(resId)).willReturn(Optional.of(receiver));
        given(friendshipBroker.propose(requester, receiver)).willReturn(mockRequest);
        given(friendRequestRepository.saveRequest(mockRequest)).willReturn(savedWithNullRelations);

        // when
        FriendRequest result = requesterService.sendRequest(reqId, resId);

        // then — 저장소가 반환한 객체(null 관계)가 아닌 저장 전 객체를 반환해야 함
        assertThat(result).isSameAs(mockRequest);
    }

    @Test
    @DisplayName("DB 유니크 제약 위반 시 DuplicateFriendRequestException으로 변환된다")
    void sendRequest_WhenConstraintViolated_ThrowsDuplicateFriendRequestException() {
        // given
        Long reqId = 1L;
        Long resId = 2L;
        SocialUser requester = mock(SocialUser.class);
        SocialUser receiver = mock(SocialUser.class);
        FriendRequest mockRequest = mock(FriendRequest.class);

        given(socialUserRepository.findById(reqId)).willReturn(Optional.of(requester));
        given(socialUserRepository.findById(resId)).willReturn(Optional.of(receiver));
        given(friendshipBroker.propose(requester, receiver)).willReturn(mockRequest);
        given(friendRequestRepository.saveRequest(mockRequest))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        // when & then
        assertThatThrownBy(() -> requesterService.sendRequest(reqId, resId))
                .isInstanceOf(DuplicateFriendRequestException.class);
    }

    @Test
    @DisplayName("신청자가 자신이 보낸 요청을 취소하면 요청이 물리 삭제된다")
    void cancelFriendRequest_Success() {
        // given
        Long requesterId = 1L;
        Long counterpartId = 2L;
        String requestId = FriendRequest.generateId(requesterId, counterpartId);

        SocialUser req = mock(SocialUser.class);
        SocialUser res = mock(SocialUser.class);
        given(req.getId()).willReturn(requesterId);
        given(res.getId()).willReturn(counterpartId);

        FriendRequest request = FriendTestFactory.createRequest(req, res);
        given(friendRequestRepository.findById(requestId)).willReturn(Optional.of(request));

        // when
        requesterService.cancelRequest(counterpartId, requesterId);

        // then
        verify(friendRequestRepository).deleteById(requestId);
    }
}

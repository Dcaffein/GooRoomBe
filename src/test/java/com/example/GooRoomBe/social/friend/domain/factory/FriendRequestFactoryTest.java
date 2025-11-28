package com.example.GooRoomBe.social.friend.domain.factory;

import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import com.example.GooRoomBe.social.friend.domain.service.FriendRequestDuplicationValidator;
import com.example.GooRoomBe.social.friend.exception.CannotRequestToSelfException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRequestFactoryTest {

    @Mock
    private SocialUserRepository socialUserRepository;
    @Mock
    private FriendRequestDuplicationValidator validator;

    @InjectMocks
    private FriendRequestFactory friendRequestFactory;

    @Test
    @DisplayName("유효한 ID로 요청 생성 시 User를 조회하고 검증 후 객체를 반환한다")
    void create_Success() {
        // given
        String reqId = "userA";
        String recvId = "userB";

        SocialUser requester = mock(SocialUser.class);
        lenient().when(requester.getId()).thenReturn(reqId);
        SocialUser receiver = mock(SocialUser.class);
        lenient().when(receiver.getId()).thenReturn(reqId);

        when(socialUserRepository.getUser(reqId)).thenReturn(requester);
        when(socialUserRepository.getUser(recvId)).thenReturn(receiver);

        // when
        FriendRequest request = friendRequestFactory.create(reqId, recvId);

        // then
        assertThat(request).isNotNull();

        // 검증기(validator)가 호출되었는지 확인 (중요!)
        verify(validator).validateNewRequest(reqId, recvId);
    }

    @Test
    @DisplayName("자기 자신에게 요청을 보내면 예외가 발생한다")
    void create_Fail_SelfRequest() {
        // given
        String myId = "userA";

        // when & then
        assertThatThrownBy(() -> friendRequestFactory.create(myId, myId))
                .isInstanceOf(CannotRequestToSelfException.class);
    }
}
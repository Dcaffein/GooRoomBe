package com.example.GooRoomBe.social.label.domain.service;

import com.example.GooRoomBe.social.friend.domain.Friendship;
import com.example.GooRoomBe.social.friend.exception.FriendshipNotFoundException;
import com.example.GooRoomBe.social.friend.infrastructure.FriendshipRepository;
import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.label.exception.InvalidLabelMemberException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelMemberServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private LabelMemberService labelMemberService;

    @Mock
    private Label label;

    @Mock
    private SocialUser owner;

    @Mock
    private SocialUser newMember;

    @Test
    @DisplayName("친구 관계인 사용자는 라벨 멤버로 추가할 수 있다")
    void addNewMember_Success() {
        // given
        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn("ownerId");
        given(newMember.getId()).willReturn("friendId");

        // 친구 관계 존재함
        given(friendshipRepository.existsFriendshipBetween("ownerId", "friendId")).willReturn(true);

        // when
        labelMemberService.addNewMember(label, newMember);

        // then
        verify(label).applyNewMember(newMember);
    }

    @Test
    @DisplayName("친구 관계가 아니면 멤버 추가 시 예외가 발생한다")
    void addNewMember_Fail_NotFriend() {
        // given
        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn("ownerId");
        given(newMember.getId()).willReturn("strangerId");

        // 친구 관계 없음
        given(friendshipRepository.existsFriendshipBetween("ownerId", "strangerId")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> labelMemberService.addNewMember(label, newMember))
                .isInstanceOf(FriendshipNotFoundException.class);

        verify(label, never()).applyNewMember(any());
    }

    @Test
    @DisplayName("멤버 교체: 요청한 ID들이 모두 친구라면 정상적으로 교체된다")
    void replaceMembers_Success() {
        // given
        String ownerId = "ownerId";
        List<String> requestIds = List.of("friend1", "friend2");

        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn(ownerId);

        Friendship fs1 = mock(Friendship.class);
        Friendship fs2 = mock(Friendship.class);
        SocialUser u1 = mock(SocialUser.class);
        SocialUser u2 = mock(SocialUser.class);

        given(fs1.getFriend(ownerId)).willReturn(u1);
        given(fs2.getFriend(ownerId)).willReturn(u2);

        // DB에서 조회된 친구 관계들
        given(friendshipRepository.filterFriendsFromIdList(eq(ownerId), eq(requestIds)))
                .willReturn(Set.of(fs1, fs2));

        // when
        labelMemberService.replaceMembers(label, requestIds);

        // then
        verify(label).replaceMembers(anySet());
    }

    @Test
    @DisplayName("멤버 교체: 친구가 아닌 ID가 포함되어 있으면 예외가 발생한다")
    void replaceMembers_Fail_InvalidMember() {
        // given
        String ownerId = "ownerId";
        // "realFriend"는 친구지만, "fakeFriend"는 친구가 아님
        List<String> requestIds = List.of("realFriend", "fakeFriend");

        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn(ownerId);

        // Mocking: DB에는 realFriend와의 관계만 있음
        Friendship fs1 = mock(Friendship.class);
        SocialUser u1 = mock(SocialUser.class);
        given(u1.getId()).willReturn("realFriend"); // 조회된 유저는 realFriend 뿐
        given(fs1.getFriend(ownerId)).willReturn(u1);

        given(friendshipRepository.filterFriendsFromIdList(eq(ownerId), eq(requestIds)))
                .willReturn(Set.of(fs1)); // 하나만 리턴됨

        // when & then
        assertThatThrownBy(() -> labelMemberService.replaceMembers(label, requestIds))
                .isInstanceOf(InvalidLabelMemberException.class)
                .hasMessageContaining("fakeFriend"); // 에러 메시지에 범인이 포함되어야 함

        verify(label, never()).replaceMembers(any());
    }

    @Test
    @DisplayName("멤버 교체: 빈 리스트가 오면 멤버를 초기화한다")
    void replaceMembers_EmptyList() {
        // given
        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn("ownerId");

        // when
        labelMemberService.replaceMembers(label, List.of());

        // then
        verify(label).replaceMembers(Set.of()); // 빈 셋으로 교체
        verify(friendshipRepository, never()).filterFriendsFromIdList(any(), any());
    }
}
package com.example.DunbarHorizon.flag.domain.invitation;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipationManager;
import static org.mockito.Mockito.mock;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagDeadlinePassedException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagParticipationDuplicateException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationAccessException;
import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationDuplicateException;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagInvitationManagerTest {

    @InjectMocks private FlagInvitationManager policy;

    @Mock private FlagRepository flagRepository;
    @Mock private FlagInvitationRepository invitationRepository;
    @Mock private FlagParticipationManager flagParticipationManager;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 1L;
    private static final Long INVITER_ID = 2L;
    private static final Long INVITEE_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag recruitingFlag() {
        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Flag flag = Flag.create(HOST_ID, "테스트 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", FLAG_ID);
        return flag;
    }

    private FlagParticipant inviterWithPermission() {
        FlagParticipant participant = mock(FlagParticipant.class);
        lenient().when(participant.isCanInvite()).thenReturn(true);
        return participant;
    }

    private FlagParticipant inviterWithoutPermission() {
        FlagParticipant participant = mock(FlagParticipant.class);
        lenient().when(participant.isCanInvite()).thenReturn(false);
        return participant;
    }

    // ==================== updateInvitePermission ====================

    @Test
    @DisplayName("호스트가 참여자에게 초대 권한을 부여할 수 있다")
    void updateInvitePermission_Grant_Success() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant participant = inviterWithoutPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(participant));

        // when
        policy.updateInvitePermission(FLAG_ID, HOST_ID, INVITER_ID, true);

        // then
        verify(participant).grantInvitePermission();
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 권한 변경을 시도하면 FlagAuthorizationException이 발생한다")
    void updateInvitePermission_NotHost_Throws() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant participant = inviterWithoutPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(participant));

        // when / then
        assertThatThrownBy(() -> policy.updateInvitePermission(FLAG_ID, INVITER_ID, INVITER_ID, true))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    // ==================== invite ====================

    @Test
    @DisplayName("초대 권한이 있는 참여자가 친구를 초대할 수 있다")
    void invite_Success() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant inviter = inviterWithPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(inviter));
        given(flagRepository.isParticipating(FLAG_ID, INVITEE_ID)).willReturn(false);
        given(invitationRepository.existsByFlagIdAndInviteeId(FLAG_ID, INVITEE_ID)).willReturn(false);

        // when
        FlagInvitation result = policy.invite(FLAG_ID, INVITER_ID, INVITEE_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getInviteeId()).isEqualTo(INVITEE_ID);
    }

    @Test
    @DisplayName("호스트는 canInvite 없이도 초대할 수 있다")
    void invite_HostIsInviter_Success() {
        // given
        Flag flag = recruitingFlag();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.isParticipating(FLAG_ID, INVITEE_ID)).willReturn(false);
        given(invitationRepository.existsByFlagIdAndInviteeId(FLAG_ID, INVITEE_ID)).willReturn(false);

        // when
        FlagInvitation result = policy.invite(FLAG_ID, HOST_ID, INVITEE_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getInviteeId()).isEqualTo(INVITEE_ID);
    }

    @Test
    @DisplayName("초대 권한이 없는 참여자가 초대하면 FlagAuthorizationException이 발생한다")
    void invite_InviterHasNoPermission_Throws() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant inviter = inviterWithoutPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(inviter));

        // when / then
        assertThatThrownBy(() -> policy.invite(FLAG_ID, INVITER_ID, INVITEE_ID))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("동일 (flagId, inviteeId) 쌍의 PENDING 초대가 이미 있으면 FlagInvitationDuplicateException이 발생한다")
    void invite_DuplicatePending_Throws() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant inviter = inviterWithPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(inviter));
        given(flagRepository.isParticipating(FLAG_ID, INVITEE_ID)).willReturn(false);
        given(invitationRepository.existsByFlagIdAndInviteeId(FLAG_ID, INVITEE_ID)).willReturn(true);

        // when / then
        assertThatThrownBy(() -> policy.invite(FLAG_ID, INVITER_ID, INVITEE_ID))
                .isInstanceOf(FlagInvitationDuplicateException.class);
    }

    @Test
    @DisplayName("호스트를 초대하면 FlagAuthorizationException이 발생한다")
    void invite_InviteeIsHost_Throws() {
        // given
        Flag flag = recruitingFlag();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));

        // when / then
        assertThatThrownBy(() -> policy.invite(FLAG_ID, INVITER_ID, HOST_ID))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("이미 참여 중인 사용자를 초대하면 FlagParticipationDuplicateException이 발생한다")
    void invite_InviteeAlreadyParticipating_Throws() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant inviter = inviterWithPermission();

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, INVITER_ID)).willReturn(Optional.of(inviter));
        given(flagRepository.isParticipating(FLAG_ID, INVITEE_ID)).willReturn(true);

        // when / then
        assertThatThrownBy(() -> policy.invite(FLAG_ID, INVITER_ID, INVITEE_ID))
                .isInstanceOf(FlagParticipationDuplicateException.class);
    }

    // ==================== accept ====================

    @Test
    @DisplayName("초대받은 사람이 수락하면 FlagParticipant가 생성된다")
    void accept_Success() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        FlagParticipant participant = mock(FlagParticipant.class);
        given(participant.getParticipantId()).willReturn(INVITEE_ID);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));
        given(flagParticipationManager.participateByInvitation(FLAG_ID, INVITEE_ID)).willReturn(participant);

        // when
        FlagParticipant result = policy.accept(10L, INVITEE_ID);

        // then
        assertThat(result.getParticipantId()).isEqualTo(INVITEE_ID);
    }

    @Test
    @DisplayName("초대 수락 시 이미 참여 중이면 FlagParticipationDuplicateException이 발생한다")
    void accept_AlreadyParticipating_Throws() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));
        given(flagParticipationManager.participateByInvitation(FLAG_ID, INVITEE_ID))
                .willThrow(new FlagParticipationDuplicateException(FLAG_ID, INVITEE_ID));

        // when / then
        assertThatThrownBy(() -> policy.accept(10L, INVITEE_ID))
                .isInstanceOf(FlagParticipationDuplicateException.class);
    }

    @Test
    @DisplayName("모집이 끝난 Flag의 초대를 수락하면 FlagDeadlinePassedException이 전파된다")
    void accept_RecruitmentClosed_Throws() {
        // given — 만료 판정은 Flag.participate()의 isRecruiting()이 수행한다
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));
        given(flagParticipationManager.participateByInvitation(FLAG_ID, INVITEE_ID))
                .willThrow(new FlagDeadlinePassedException());

        // when / then
        assertThatThrownBy(() -> policy.accept(10L, INVITEE_ID))
                .isInstanceOf(FlagDeadlinePassedException.class);
    }

    @Test
    @DisplayName("초대받지 않은 사람이 수락하면 FlagInvitationAccessException이 발생한다")
    void accept_NotInvitee_Throws() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));

        // when / then
        Long otherId = 99L;
        assertThatThrownBy(() -> policy.accept(10L, otherId))
                .isInstanceOf(FlagInvitationAccessException.class);
    }

    // ==================== reject ====================

    @Test
    @DisplayName("초대받은 사람이 거절하면 검증을 통과한다")
    void reject_Success() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));

        // when / then
        assertThatNoException().isThrownBy(() -> policy.reject(10L, INVITEE_ID));
    }

    // ==================== cancel ====================

    @Test
    @DisplayName("초대를 보낸 사람이 PENDING 초대를 취소할 수 있다")
    void cancel_Success() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));

        // when / then
        assertThatNoException().isThrownBy(() -> policy.cancel(10L, INVITER_ID));
    }

    @Test
    @DisplayName("초대를 보내지 않은 사람이 취소하면 FlagInvitationAccessException이 발생한다")
    void cancel_NotInviter_Throws() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(invitation, "id", 10L);

        given(invitationRepository.findById(10L)).willReturn(Optional.of(invitation));

        // when / then
        Long otherId = 99L;
        assertThatThrownBy(() -> policy.cancel(10L, otherId))
                .isInstanceOf(FlagInvitationAccessException.class);
    }
}

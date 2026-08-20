package com.example.DunbarHorizon.flag.application.service.invitation;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitationManager;
import com.example.DunbarHorizon.flag.domain.invitation.event.FlagInvitationSentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagInvitationServiceTest {

    @InjectMocks private FlagInvitationCommandService flagInvitationService;

    @Mock private FlagInvitationManager invitationManager;
    @Mock private FlagInvitationRepository invitationRepository;
    @Mock private FlagRepository flagRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 1L;
    private static final Long INVITER_ID = 2L;
    private static final Long INVITEE_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    @DisplayName("초대 생성 시 Manager에 위임하고 저장 후 이벤트를 발행한다")
    void invite_DelegatesToPolicyAndPublishesEvent() {
        // given
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        FlagInvitation savedInvitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(savedInvitation, "id", 10L);

        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Flag flag = Flag.create(HOST_ID, "테스트 플래그", "설명", 10, schedule);

        given(invitationManager.invite(FLAG_ID, INVITER_ID, INVITEE_ID)).willReturn(invitation);
        given(invitationRepository.save(invitation)).willReturn(savedInvitation);
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));

        // when
        Long resultId = flagInvitationService.invite(FLAG_ID, INVITER_ID, INVITEE_ID);

        // then
        assertThat(resultId).isEqualTo(10L);
        verify(invitationManager).invite(FLAG_ID, INVITER_ID, INVITEE_ID);
        verify(invitationRepository).save(invitation);

        ArgumentCaptor<FlagInvitationSentEvent> eventCaptor = ArgumentCaptor.forClass(FlagInvitationSentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        FlagInvitationSentEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.invitationId()).isEqualTo(10L);
        assertThat(publishedEvent.inviteeId()).isEqualTo(INVITEE_ID);
    }

    @Test
    @DisplayName("수락 시 Manager에 위임하고 FlagParticipant를 저장한 뒤 초대를 삭제한다")
    void accept_DelegatesToPolicyAndSavesParticipantAndDeletes() {
        // given
        FlagParticipant newParticipant = mock(FlagParticipant.class);
        given(invitationManager.accept(10L, INVITEE_ID)).willReturn(newParticipant);

        // when
        flagInvitationService.accept(10L, INVITEE_ID);

        // then
        verify(invitationManager).accept(10L, INVITEE_ID);
        verify(flagRepository).saveParticipant(newParticipant);
        verify(invitationRepository).deleteById(10L);
    }

    @Test
    @DisplayName("거절 시 Manager에 위임하고 초대를 삭제한다")
    void reject_DelegatesToPolicyAndDeletes() {
        // when
        flagInvitationService.reject(10L, INVITEE_ID);

        // then
        verify(invitationManager).reject(10L, INVITEE_ID);
        verify(invitationRepository).deleteById(10L);
    }

    @Test
    @DisplayName("취소 시 Manager에 위임하고 초대를 삭제한다")
    void cancel_DelegatesToManagerAndDeletes() {
        // when
        flagInvitationService.cancel(10L, INVITER_ID);

        // then
        verify(invitationManager).cancel(10L, INVITER_ID);
        verify(invitationRepository).deleteById(10L);
    }
}

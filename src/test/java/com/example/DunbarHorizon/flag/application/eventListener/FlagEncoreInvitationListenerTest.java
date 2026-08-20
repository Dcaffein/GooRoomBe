package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.event.FlagInvitationSentEvent;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagEncoreInvitationListenerTest {

    @InjectMocks private FlagEncoreInvitationListener listener;
    @Mock private FlagRepository flagRepository;
    @Mock private FlagInvitationRepository invitationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long PARENT_FLAG_ID = 1L;
    private static final Long ENCORE_FLAG_ID = 2L;
    private static final Long HOST_ID = 10L;
    private static final String TITLE = "같이 밥 먹어요";

    private Flag encoreFlag;
    private FlagEncoreEvent event;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        FlagSchedule schedule = FlagSchedule.of(now.plusDays(1), now.plusDays(2), now.plusDays(2).plusHours(2));
        encoreFlag = Flag.create(HOST_ID, TITLE, "설명", 10, schedule);
        ReflectionTestUtils.setField(encoreFlag, "id", ENCORE_FLAG_ID);

        event = new FlagEncoreEvent(PARENT_FLAG_ID, HOST_ID, TITLE);
    }

    @Test
    @DisplayName("정상 — 부모 참여자 3명에게 초대장 3개를 생성하고 FlagInvitationSentEvent를 3회 발행한다")
    void handle_정상_부모참여자3명_초대장3개_생성() {
        // given
        List<Long> parentParticipants = List.of(20L, 30L, 40L);
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(PARENT_FLAG_ID)).willReturn(parentParticipants);
        given(flagRepository.findAllParticipantIds(ENCORE_FLAG_ID)).willReturn(List.of());
        given(invitationRepository.findInviteeIdsByFlagId(ENCORE_FLAG_ID)).willReturn(Set.of());
        given(invitationRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<List<FlagInvitation>> captor = ArgumentCaptor.forClass(List.class);
        verify(invitationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);

        ArgumentCaptor<FlagInvitationSentEvent> eventCaptor = ArgumentCaptor.forClass(FlagInvitationSentEvent.class);
        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).allMatch(FlagInvitationSentEvent::isEncore);
    }

    @Test
    @DisplayName("호스트 본인이 부모 참여자에 포함된 경우 제외하고 나머지만 초대한다")
    void handle_호스트_본인_제외() {
        // given
        List<Long> parentParticipants = List.of(HOST_ID, 20L, 30L); // HOST_ID 포함
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(PARENT_FLAG_ID)).willReturn(parentParticipants);
        given(flagRepository.findAllParticipantIds(ENCORE_FLAG_ID)).willReturn(List.of());
        given(invitationRepository.findInviteeIdsByFlagId(ENCORE_FLAG_ID)).willReturn(Set.of());
        given(invitationRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<List<FlagInvitation>> captor = ArgumentCaptor.forClass(List.class);
        verify(invitationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).noneMatch(i -> i.getInviteeId().equals(HOST_ID));
    }

    @Test
    @DisplayName("이미 pending 초대가 존재하는 경우 해당 invitee를 제외한다")
    void handle_이미_pending_초대_제외() {
        // given
        List<Long> parentParticipants = List.of(20L, 30L, 40L);
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(PARENT_FLAG_ID)).willReturn(parentParticipants);
        given(flagRepository.findAllParticipantIds(ENCORE_FLAG_ID)).willReturn(List.of());
        given(invitationRepository.findInviteeIdsByFlagId(ENCORE_FLAG_ID)).willReturn(Set.of(20L));
        given(invitationRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<List<FlagInvitation>> captor = ArgumentCaptor.forClass(List.class);
        verify(invitationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).noneMatch(i -> i.getInviteeId().equals(20L));
    }

    @Test
    @DisplayName("이미 encore에 참여 중인 경우 해당 invitee를 제외한다")
    void handle_이미_encore_참여중_제외() {
        // given
        List<Long> parentParticipants = List.of(20L, 30L, 40L);
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(PARENT_FLAG_ID)).willReturn(parentParticipants);
        given(flagRepository.findAllParticipantIds(ENCORE_FLAG_ID)).willReturn(List.of(30L));
        given(invitationRepository.findInviteeIdsByFlagId(ENCORE_FLAG_ID)).willReturn(Set.of());
        given(invitationRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<List<FlagInvitation>> captor = ArgumentCaptor.forClass(List.class);
        verify(invitationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).noneMatch(i -> i.getInviteeId().equals(30L));
    }

    @Test
    @DisplayName("부모 참여자가 없으면 saveAll을 호출하지 않는다")
    void handle_부모참여자없음_saveAll_미호출() {
        // given
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(PARENT_FLAG_ID)).willReturn(List.of());

        // when
        listener.handle(event);

        // then
        verify(invitationRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("encore flag가 모집 중이 아니면 saveAll을 호출하지 않는다")
    void handle_모집중아님_saveAll_미호출() {
        // given — 마감 시각을 과거로 설정해 RECRUITING이 아닌 상태로 만듦
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        FlagSchedule expiredSchedule = FlagSchedule.of(past, past.plusHours(1), past.plusHours(2));
        Flag expiredEncoreFlag = Flag.create(HOST_ID, TITLE, "설명", 10, expiredSchedule);
        ReflectionTestUtils.setField(expiredEncoreFlag, "id", ENCORE_FLAG_ID);

        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.of(expiredEncoreFlag));

        // when
        listener.handle(event);

        // then
        verify(invitationRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("encore flag가 존재하지 않으면 saveAll을 호출하지 않는다")
    void handle_encore_flag_없음_saveAll_미호출() {
        // given
        given(flagRepository.findByParentId(PARENT_FLAG_ID)).willReturn(Optional.empty());

        // when
        listener.handle(event);

        // then
        verify(invitationRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

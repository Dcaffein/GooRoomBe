package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagDeletedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.global.event.notification.NotificationEvent;
import com.example.DunbarHorizon.global.event.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagDeletionEventListenerTest {

    @InjectMocks
    private FlagDeletionEventListener listener;

    @Mock private FlagRepository flagRepository;
    @Mock private FlagExpiryExemptionUpdater flagExpiryExemptionUpdater;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private static final String FLAG_TITLE = "테스트 플래그";
    private static final List<Long> PARTICIPANT_IDS = List.of(21L, 22L, 23L);

    private FlagDeletedEvent recruitingEvent() {
        return eventOf(FlagStatus.RECRUITING);
    }

    private FlagDeletedEvent endedEventWithParent(Long parentId) {
        return new FlagDeletedEvent(FLAG_ID, HOST_ID, parentId, FLAG_TITLE, FlagStatus.ENDED);
    }

    private FlagDeletedEvent eventOf(FlagStatus status) {
        return new FlagDeletedEvent(FLAG_ID, HOST_ID, null, FLAG_TITLE, status);
    }

    private void givenParticipants(List<Long> participantIds) {
        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.empty());
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(participantIds);
    }

    private FlagConcludedEvent capturedConclusion() {
        ArgumentCaptor<FlagConcludedEvent> captor = ArgumentCaptor.forClass(FlagConcludedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private NotificationEvent capturedNotification() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("soft delete된 Flag를 findById로 재조회하지 않는다")
    void handleFlagDeletion_doesNotFindDeletedFlag() {
        // given
        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.empty());
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(recruitingEvent());

        // then
        verify(flagRepository, never()).findById(FLAG_ID);
    }

    @Test
    @DisplayName("encore가 존재하면 parentId 연결을 끊는다")
    void handleFlagDeletion_sevensEncoreParentLink() {
        // given
        Flag encoreFlag = Flag.create(HOST_ID, "앵콜", "설명", 5,
                FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)));
        ReflectionTestUtils.setField(encoreFlag, "parentId", FLAG_ID);

        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(recruitingEvent());

        // then
        assertThat(encoreFlag.getParentId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = FlagStatus.class, names = {"RECRUITING", "WAITING", "IN_ACTIVITY"})
    @DisplayName("종료되지 않은 Flag를 삭제하면 참여자 전원에게 취소 알림이 간다")
    void handleFlagDeletion_notifiesParticipants_whenNotEnded(FlagStatus status) {
        // given
        givenParticipants(PARTICIPANT_IDS);

        // when
        listener.handleFlagDeletion(eventOf(status));

        // then
        NotificationEvent notification = capturedNotification();
        assertThat(notification.receiverIds()).containsExactlyElementsOf(PARTICIPANT_IDS);
        assertThat(notification.type()).isEqualTo(NotificationType.FLAG_CANCELED);
        assertThat(notification.content()).contains(FLAG_TITLE);
    }

    @ParameterizedTest
    @EnumSource(value = FlagStatus.class, names = {"RECRUITING", "WAITING", "IN_ACTIVITY"})
    @DisplayName("종료되지 않은 Flag를 삭제하면 모임이 열린 것으로 치지 않는다")
    void handleFlagDeletion_doesNotConclude_whenNotEnded(FlagStatus status) {
        // given
        givenParticipants(PARTICIPANT_IDS);

        // when
        listener.handleFlagDeletion(eventOf(status));

        // then
        verify(eventPublisher, never()).publishEvent(any(FlagConcludedEvent.class));
    }

    @Test
    @DisplayName("종료된 Flag를 삭제하면 취소 알림 대신 종료 사실을 발행한다")
    void handleFlagDeletion_concludes_whenEnded() {
        // given
        givenParticipants(PARTICIPANT_IDS);

        // when
        listener.handleFlagDeletion(eventOf(FlagStatus.ENDED));

        // then
        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));

        FlagConcludedEvent concluded = capturedConclusion();
        assertThat(concluded.flagId()).isEqualTo(FLAG_ID);
        assertThat(concluded.hostId()).isEqualTo(HOST_ID);
        assertThat(concluded.participantIds()).containsExactlyElementsOf(PARTICIPANT_IDS);
        assertThat(concluded.isEncore()).isFalse();
    }

    @Test
    @DisplayName("앵콜 Flag가 종료 후 삭제되면 parentId를 실어 발행한다")
    void handleFlagDeletion_concludes_carriesParentId() {
        // given
        Long parentId = 99L;
        givenParticipants(PARTICIPANT_IDS);

        // when
        listener.handleFlagDeletion(endedEventWithParent(parentId));

        // then
        FlagConcludedEvent concluded = capturedConclusion();
        assertThat(concluded.parentId()).isEqualTo(parentId);
        assertThat(concluded.isEncore()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(FlagStatus.class)
    @DisplayName("참여자가 없으면 어떤 상태에서도 종료 사실을 발행하지 않는다")
    void handleFlagDeletion_doesNotConclude_whenNoParticipants(FlagStatus status) {
        // given
        givenParticipants(List.of());

        // when
        listener.handleFlagDeletion(eventOf(status));

        // then
        verify(eventPublisher, never()).publishEvent(any(FlagConcludedEvent.class));
    }

    @ParameterizedTest
    @EnumSource(FlagStatus.class)
    @DisplayName("참여자가 없으면 어떤 상태에서도 취소 알림을 보내지 않는다")
    void handleFlagDeletion_doesNotNotify_whenNoParticipants(FlagStatus status) {
        // given
        givenParticipants(List.of());

        // when
        listener.handleFlagDeletion(eventOf(status));

        // then
        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("parentId가 있으면 부모 Flag의 만료 면제 재계산을 시도한다")
    void handleFlagDeletion_refreshesParentExemption() {
        // given
        Long parentId = 99L;
        FlagDeletedEvent event = endedEventWithParent(parentId);

        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.empty());
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(event);

        // then
        verify(flagExpiryExemptionUpdater).refresh(parentId);
    }
}

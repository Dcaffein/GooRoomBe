package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagExpiryTarget;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagExpiryServiceTest {

    @InjectMocks private FlagExpiryService flagExpiryService;
    @Mock private FlagRepository flagRepository;
    @Mock private FlagMaintenancePort maintenancePort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final int BATCH_SIZE = 5000;

    private record Target(Long id, Long hostId, Long parentId) implements FlagExpiryTarget {
        @Override public Long getId() { return id; }
        @Override public Long getHostId() { return hostId; }
        @Override public Long getParentId() { return parentId; }
    }

    private void givenTargets(List<FlagExpiryTarget> targets, Map<Long, List<Long>> participants) {
        given(flagRepository.findExpiryTargets(any(), anyInt())).willReturn(targets);
        given(flagRepository.findAllParticipantIdsByFlagIds(any())).willReturn(participants);
        given(flagRepository.expireByIds(any(), any())).willReturn(targets.size());
    }

    private List<FlagConcludedEvent> publishedConclusions() {
        ArgumentCaptor<FlagConcludedEvent> captor = ArgumentCaptor.forClass(FlagConcludedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("만료 임계값이 현재 시각 기준 24시간 전으로 계산된다")
    void expireEndedFlags_ThresholdIs24HoursBefore() {
        // given
        givenTargets(List.of(), Map.of());
        LocalDateTime before = LocalDateTime.now().minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        // when
        flagExpiryService.expireEndedFlags();

        // then
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(flagRepository).findExpiryTargets(thresholdCaptor.capture(), anyInt());
        assertThat(thresholdCaptor.getValue()).isBetween(before.minusSeconds(1), before.plusSeconds(1));
    }

    @Test
    @DisplayName("조회한 대상의 id로만 소프트 삭제한다")
    void expireEndedFlags_ExpiresQueriedIds() {
        // given
        givenTargets(List.of(new Target(1L, 10L, null), new Target(2L, 11L, null)),
                Map.of(1L, List.of(21L), 2L, List.of(22L)));

        // when
        flagExpiryService.expireEndedFlags();

        // then
        ArgumentCaptor<Collection<Long>> idCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(flagRepository).expireByIds(idCaptor.capture(), any(LocalDateTime.class));
        assertThat(idCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("참여자를 한 번에 조회해 플래그마다 종료 사실을 발행한다")
    void expireEndedFlags_PublishesConclusionPerFlag() {
        // given
        givenTargets(List.of(new Target(1L, 10L, null), new Target(2L, 11L, 99L)),
                Map.of(1L, List.of(21L, 22L), 2L, List.of(23L)));

        // when
        flagExpiryService.expireEndedFlags();

        // then
        verify(flagRepository).findAllParticipantIdsByFlagIds(List.of(1L, 2L));

        List<FlagConcludedEvent> published = publishedConclusions();
        assertThat(published).hasSize(2);
        assertThat(published.get(0).flagId()).isEqualTo(1L);
        assertThat(published.get(0).hostId()).isEqualTo(10L);
        assertThat(published.get(0).participantIds()).containsExactly(21L, 22L);
        assertThat(published.get(0).isEncore()).isFalse();
        assertThat(published.get(1).participantIds()).containsExactly(23L);
        assertThat(published.get(1).isEncore()).isTrue();
    }

    @Test
    @DisplayName("참여자가 없는 플래그는 소프트 삭제만 하고 발행하지 않는다")
    void expireEndedFlags_NoParticipants_PublishesNothing() {
        // given
        givenTargets(List.of(new Target(1L, 10L, null)), Map.of());

        // when
        flagExpiryService.expireEndedFlags();

        // then
        verify(flagRepository).expireByIds(any(), any());
        verify(eventPublisher, never()).publishEvent(any(FlagConcludedEvent.class));
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무것도 발행하지 않는다")
    void expireEndedFlags_NoTargets_PublishesNothing() {
        // given
        givenTargets(List.of(), Map.of());

        // when
        flagExpiryService.expireEndedFlags();

        // then
        verify(eventPublisher, never()).publishEvent(any(FlagConcludedEvent.class));
    }

    @Test
    @DisplayName("한 회차가 가져가는 대상 수에 상한이 있다")
    void expireEndedFlags_LimitsBatchSize() {
        // given
        givenTargets(List.of(), Map.of());

        // when
        flagExpiryService.expireEndedFlags();

        // then
        verify(flagRepository).findExpiryTargets(any(LocalDateTime.class), eq(BATCH_SIZE));
    }

    @Test
    @DisplayName("종료된 플래그의 초대를 정리한 뒤에 플래그를 소프트 삭제한다")
    void expireEndedFlags_PurgesInvitationsBeforeSoftDelete() {
        // given
        given(maintenancePort.purgeInvitationsOfEndedFlags(any())).willReturn(2);
        givenTargets(List.of(new Target(1L, 10L, null)), Map.of(1L, List.of(21L)));

        // when
        flagExpiryService.expireEndedFlags();

        // then
        // 순서가 뒤집히면 방금 소프트 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
        InOrder inOrder = inOrder(maintenancePort, flagRepository);
        inOrder.verify(maintenancePort).purgeInvitationsOfEndedFlags(any(LocalDateTime.class));
        inOrder.verify(flagRepository).findExpiryTargets(any(), anyInt());
        inOrder.verify(flagRepository).expireByIds(any(), any());
    }

    @Test
    @DisplayName("초대 정리와 플래그 만료가 동일한 임계값을 사용한다")
    void expireEndedFlags_SharesThreshold() {
        // given
        given(maintenancePort.purgeInvitationsOfEndedFlags(any())).willReturn(0);
        givenTargets(List.of(), Map.of());

        // when
        flagExpiryService.expireEndedFlags();

        // then
        ArgumentCaptor<LocalDateTime> invitationThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> flagThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(maintenancePort).purgeInvitationsOfEndedFlags(invitationThreshold.capture());
        verify(flagRepository).findExpiryTargets(flagThreshold.capture(), anyInt());

        assertThat(invitationThreshold.getValue()).isEqualTo(flagThreshold.getValue());
    }
}

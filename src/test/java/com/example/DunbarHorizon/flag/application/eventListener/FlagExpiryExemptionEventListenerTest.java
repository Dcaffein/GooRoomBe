package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagExpiryExemptedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagExpiryExemptionEventListenerTest {

    @InjectMocks
    private FlagExpiryExemptionEventListener listener;

    @Mock private FlagRepository flagRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 10L;
    private static final List<Long> PARTICIPANT_IDS = List.of(21L, 22L);

    @Test
    @DisplayName("만료 면제가 붙으면 참여자를 조회해 종료 사실을 발행한다")
    void handleExpiryExempted_publishesConclusion() {
        // given
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(PARTICIPANT_IDS);

        // when
        listener.handleExpiryExempted(new FlagExpiryExemptedEvent(FLAG_ID, HOST_ID, 99L));

        // then
        ArgumentCaptor<FlagConcludedEvent> captor = ArgumentCaptor.forClass(FlagConcludedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().flagId()).isEqualTo(FLAG_ID);
        assertThat(captor.getValue().hostId()).isEqualTo(HOST_ID);
        assertThat(captor.getValue().participantIds()).containsExactlyElementsOf(PARTICIPANT_IDS);
        assertThat(captor.getValue().isEncore()).isTrue();
    }

    @Test
    @DisplayName("참여자가 없으면 종료 사실을 발행하지 않는다")
    void handleExpiryExempted_noParticipants_publishesNothing() {
        // given
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleExpiryExempted(new FlagExpiryExemptedEvent(FLAG_ID, HOST_ID, null));

        // then
        verify(eventPublisher, never()).publishEvent(any(FlagConcludedEvent.class));
    }
}

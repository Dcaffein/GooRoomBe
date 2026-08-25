package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.global.event.interaction.BatchMutualInteractionEvent;
import com.example.DunbarHorizon.global.event.interaction.InteractionType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagConclusionEventListenerTest {

    @InjectMocks
    private FlagConclusionEventListener listener;

    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 10L;
    private static final List<Long> PARTICIPANT_IDS = List.of(21L, 22L, 23L);

    private BatchMutualInteractionEvent capturedInteraction() {
        ArgumentCaptor<BatchMutualInteractionEvent> captor =
                ArgumentCaptor.forClass(BatchMutualInteractionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("이벤트에 실려온 참여자와 호스트를 그대로 상호작용 이벤트로 넘긴다")
    void handleFlagConclusion_forwardsParticipants() {
        // when
        listener.handleFlagConclusion(
                new FlagConcludedEvent(FLAG_ID, HOST_ID, null, PARTICIPANT_IDS));

        // then
        BatchMutualInteractionEvent event = capturedInteraction();
        assertThat(event.participantIds()).containsExactlyElementsOf(PARTICIPANT_IDS);
        assertThat(event.hostId()).isEqualTo(HOST_ID);
        assertThat(event.type()).isEqualTo(InteractionType.FLAG_ENDED);
    }

    @Test
    @DisplayName("앵코르 플래그는 FLAG_ENDED_ENCORE로 넘긴다")
    void handleFlagConclusion_encoreUsesEncoreType() {
        // when
        listener.handleFlagConclusion(
                new FlagConcludedEvent(FLAG_ID, HOST_ID, 99L, PARTICIPANT_IDS));

        // then
        assertThat(capturedInteraction().type()).isEqualTo(InteractionType.FLAG_ENDED_ENCORE);
    }

    @Test
    @DisplayName("참여자 없는 종료 사실은 만들 수 없다")
    void flagConcludedEvent_rejectsEmptyParticipants() {
        assertThatThrownBy(() -> new FlagConcludedEvent(FLAG_ID, HOST_ID, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new FlagConcludedEvent(FLAG_ID, HOST_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagPreservationPolicy;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagDeletedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.global.event.interaction.BatchMutualInteractionEvent;
import com.example.DunbarHorizon.global.event.interaction.InteractionType;
import com.example.DunbarHorizon.global.event.notification.NotificationEvent;
import com.example.DunbarHorizon.global.event.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FlagDeletionEventListener {

    private final FlagRepository flagRepository;
    private final FlagPreservationPolicy flagPreservationPolicy;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFlagDeletion(FlagDeletedEvent event) {
        Optional<Flag> encoreResult = flagRepository.findByParentId(event.flagId());
        encoreResult.ifPresent(Flag::severParentLink);

        notifyParticipants(event, event.hostId());

        if (event.parentId() != null) {
            flagPreservationPolicy.refresh(event.parentId());
        }
    }

    // 참여자 삭제는 하드 퍼지 배치가 맡는다. 이 메서드는 발행만 하므로 재실행 시
    // 알림이 중복될 수 있다 — 재시도나 아웃박스를 도입하면 멱등 키가 필요하다.
    private void notifyParticipants(FlagDeletedEvent event, Long hostId) {
        List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());

        if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.RECRUITING) {
            publishNotification(participantIds, event.flagTitle());
        }

        if (!participantIds.isEmpty() && isMeetingHeld(event.statusAtDeletion())) {
            publishInteractionEvents(participantIds, hostId, event.parentId() != null);
        }
    }

    private boolean isMeetingHeld(FlagStatus status) {
        return status == FlagStatus.ENDED || status == FlagStatus.IN_ACTIVITY;
    }

    private void publishInteractionEvents(List<Long> participantIds, Long hostId, boolean isEncore) {
        InteractionType type = isEncore ? InteractionType.FLAG_ENDED_ENCORE : InteractionType.FLAG_ENDED;
        eventPublisher.publishEvent(new BatchMutualInteractionEvent(participantIds, hostId, type));
    }

    private void publishNotification(List<Long> receiverIds, String title) {
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .receiverIds(receiverIds)
                .title("모임 취소 안내")
                .content(String.format("[%s] 모임이 호스트 사정으로 취소되었습니다.", title))
                .type(NotificationType.FLAG_CANCELED)
                .build();

        eventPublisher.publishEvent(notificationEvent);
    }
}
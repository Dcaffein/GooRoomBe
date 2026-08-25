package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagDeletedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
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
    private final FlagExpiryExemptionUpdater flagExpiryExemptionUpdater;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFlagDeletion(FlagDeletedEvent event) {
        Optional<Flag> encoreResult = flagRepository.findByParentId(event.flagId());
        encoreResult.ifPresent(Flag::severParentLink);

        // 플래그는 방금 소프트 삭제됐지만 flag_participants에는 @SQLRestriction이 없다.
        // 참여자 행은 퍼지 배치가 나중에 지우므로 이 시점에는 남아 있다.
        List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());

        if (!participantIds.isEmpty()) {
            if (event.statusAtDeletion() == FlagStatus.ENDED) {
                eventPublisher.publishEvent(new FlagConcludedEvent(
                        event.flagId(), event.hostId(), event.parentId(), participantIds));
            } else {
                notifyFlagCancel(participantIds, event.flagTitle());
            }
        }

        if (event.parentId() != null) {
            flagRepository.save(flagExpiryExemptionUpdater.refresh(event.parentId()));
        }
    }

    private void notifyFlagCancel(List<Long> receiverIds, String title) {
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .receiverIds(receiverIds)
                .title("모임 취소 안내")
                .content(String.format("[%s] 모임이 호스트 사정으로 취소되었습니다.", title))
                .type(NotificationType.FLAG_CANCELED)
                .build();

        eventPublisher.publishEvent(notificationEvent);
    }
}
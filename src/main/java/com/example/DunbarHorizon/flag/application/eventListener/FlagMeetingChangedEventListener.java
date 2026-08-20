package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.event.FlagMeetingChangedEvent;
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

@Component
@RequiredArgsConstructor
public class FlagMeetingChangedEventListener {

    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(FlagMeetingChangedEvent event) {
        List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());

        if (participantIds.isEmpty()) return;

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .receiverIds(participantIds)
                .title("모임 시간 변경 안내")
                .content(String.format("[%s] 모임 시간이 변경되었습니다. 새로운 일정을 확인해주세요!", event.flagTitle()))
                .type(NotificationType.FLAG_SCHEDULE_CHANGED)
                .build();

        eventPublisher.publishEvent(notificationEvent);
    }
}

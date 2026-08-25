package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagExpiryExemptedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
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
public class FlagExpiryExemptionEventListener {

    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleExpiryExempted(FlagExpiryExemptedEvent event) {
        List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());

        if (participantIds.isEmpty()) return;

        eventPublisher.publishEvent(new FlagConcludedEvent(
                event.flagId(), event.hostId(), event.parentId(), participantIds));
    }
}

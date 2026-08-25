package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialCreatedEvent;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FlagMemorialEventListener {

    private final FlagExpiryExemptionUpdater flagExpiryExemptionUpdater;
    private final FlagRepository flagRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleMemorialCreated(MemorialCreatedEvent event) {
        flagRepository.save(flagExpiryExemptionUpdater.refresh(event.flagId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleMemorialDeleted(MemorialDeletedEvent event) {
        flagRepository.save(flagExpiryExemptionUpdater.refresh(event.flagId()));
    }
}

package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionPolicy;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialCreatedEvent;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FlagMemorialEventListener {

    private final FlagExpiryExemptionPolicy flagExpiryExemptionPolicy;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleMemorialCreated(MemorialCreatedEvent event) {
        flagExpiryExemptionPolicy.refresh(event.flagId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleMemorialDeleted(MemorialDeletedEvent event) {
        flagExpiryExemptionPolicy.refresh(event.flagId());
    }
}

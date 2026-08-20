package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionPolicy;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FlagEncoreEventListener {

    private final FlagExpiryExemptionPolicy flagExpiryExemptionPolicy;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleEncoreCreated(FlagEncoreEvent event) {
        flagExpiryExemptionPolicy.refresh(event.parentFlagId());
    }
}
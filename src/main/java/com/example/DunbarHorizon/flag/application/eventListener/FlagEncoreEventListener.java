package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FlagEncoreEventListener {

    private final FlagExpiryExemptionUpdater flagExpiryExemptionUpdater;
    private final FlagRepository flagRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleEncoreCreated(FlagEncoreEvent event) {
        flagRepository.save(flagExpiryExemptionUpdater.refresh(event.parentFlagId()));
    }
}
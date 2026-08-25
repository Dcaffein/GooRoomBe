package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialCreatedEvent;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT인 이유는 후기 작성과 만료 면제 갱신이 한 트랜잭션이어야 하기 때문이다.
 * AFTER_COMMIT이면 면제 갱신만 따로 실패해 후기가 달렸는데도 자동 만료로 지워질 수 있다.
 */
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

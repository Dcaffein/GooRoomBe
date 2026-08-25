package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionUpdater;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 갱신 대상은 새로 만들어진 앵코르가 아니라 부모 플래그다. 앵코르가 달렸다는 것은
 * 부모를 자동 만료에서 빼야 한다는 뜻이다. BEFORE_COMMIT인 이유는
 * {@link FlagMemorialEventListener}와 같다.
 */
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
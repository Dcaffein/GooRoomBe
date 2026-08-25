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

/**
 * "만료 면제가 붙었다"를 "모임이 실제로 열렸다"로 번역하는 자리다.
 * 성립하는 근거는 면제를 만드는 후기와 앵코르가 종료된 플래그에만 달린다는 것이다
 * (FlagMemorialFactory, Flag.createEncore). 면제 원천을 추가한다면 그 원천도 같은
 * 성질을 갖는지 여기서 따져야 한다.
 */
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

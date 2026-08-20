package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.event.FlagInvitationSentEvent;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FlagEncoreInvitationListener {

    private final FlagRepository flagRepository;
    private final FlagInvitationRepository invitationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(FlagEncoreEvent event) {
        Flag encoreFlag = flagRepository.findByParentId(event.parentFlagId()).orElse(null);
        if (encoreFlag == null || !encoreFlag.isRecruiting()) return;

        List<Long> parentParticipantIds = flagRepository.findAllParticipantIds(event.parentFlagId());
        if (parentParticipantIds.isEmpty()) return;

        Set<Long> alreadyInvited      = invitationRepository.findInviteeIdsByFlagId(encoreFlag.getId());
        Set<Long> alreadyParticipating = new HashSet<>(flagRepository.findAllParticipantIds(encoreFlag.getId()));

        List<FlagInvitation> invitations = parentParticipantIds.stream()
                .filter(id -> !id.equals(event.hostId()))
                .filter(id -> !alreadyInvited.contains(id))
                .filter(id -> !alreadyParticipating.contains(id))
                .map(id -> FlagInvitation.create(encoreFlag.getId(), event.hostId(), id))
                .toList();

        if (invitations.isEmpty()) return;

        List<FlagInvitation> saved = invitationRepository.saveAll(invitations);
        saved.forEach(inv -> eventPublisher.publishEvent(
                new FlagInvitationSentEvent(inv.getFlagId(), inv.getId(), inv.getInviteeId(), event.title(), true)
        ));
    }
}

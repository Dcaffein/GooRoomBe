package com.example.DunbarHorizon.flag.application.service.invitation;

import com.example.DunbarHorizon.flag.application.port.in.FlagInvitationUseCase;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitationManager;
import com.example.DunbarHorizon.flag.domain.invitation.event.FlagInvitationSentEvent;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FlagInvitationCommandService implements FlagInvitationUseCase {

    private final FlagInvitationManager invitationManager;
    private final FlagInvitationRepository invitationRepository;
    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void updateInvitePermission(Long flagId, Long requesterId, Long participantUserId, boolean canInvite) {
        FlagParticipant participant =
                invitationManager.updateInvitePermission(flagId, requesterId, participantUserId, canInvite);
        flagRepository.saveParticipant(participant);
    }

    @Override
    public Long invite(Long flagId, Long inviterId, Long inviteeId) {
        FlagInvitation invitation = invitationManager.invite(flagId, inviterId, inviteeId);
        FlagInvitation saved = invitationRepository.save(invitation);

        // invitationManager가 이미 Flag 존재를 검증했다. 같은 트랜잭션이라 1차 캐시 히트다.
        String flagTitle = flagRepository.findById(flagId)
                .map(Flag::getTitle)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        eventPublisher.publishEvent(new FlagInvitationSentEvent(
                flagId, saved.getId(), inviteeId, flagTitle, false
        ));

        return saved.getId();
    }

    @Override
    public void accept(Long invitationId, Long acceptorId) {
        FlagParticipant newParticipant = invitationManager.accept(invitationId, acceptorId);
        flagRepository.saveParticipant(newParticipant);
        invitationRepository.deleteById(invitationId);
    }

    @Override
    public void reject(Long invitationId, Long rejectorId) {
        invitationManager.reject(invitationId, rejectorId);
        invitationRepository.deleteById(invitationId);
    }

    @Override
    public void cancel(Long invitationId, Long requesterId) {
        invitationManager.cancel(invitationId, requesterId);
        invitationRepository.deleteById(invitationId);
    }
}

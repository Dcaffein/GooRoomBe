package com.example.DunbarHorizon.flag.application.port.in;

import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitationStatus;

public interface FlagInvitationUseCase {
    void updateInvitePermission(Long flagId, Long requesterId, Long participantUserId, boolean canInvite);
    Long invite(Long flagId, Long inviterId, Long inviteeId);
    void updateStatus(Long invitationId, Long requesterId, FlagInvitationStatus status);
    void delete(Long invitationId, Long requesterId);
}

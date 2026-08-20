package com.example.DunbarHorizon.flag.domain.invitation.repository;

import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FlagInvitationRepository {
    FlagInvitation save(FlagInvitation invitation);
    List<FlagInvitation> saveAll(List<FlagInvitation> invitations);
    Optional<FlagInvitation> findById(Long id);
    List<FlagInvitation> findByInviteeId(Long inviteeId);
    List<FlagInvitation> findByInviterId(Long inviterId);
    boolean existsByFlagIdAndInviteeId(Long flagId, Long inviteeId);
    Set<Long> findInviteeIdsByFlagId(Long flagId);
    void deleteById(Long id);
    void hardDeleteByFlagIdsIn(Collection<Long> flagIds);
}

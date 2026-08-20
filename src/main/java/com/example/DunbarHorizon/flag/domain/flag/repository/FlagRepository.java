package com.example.DunbarHorizon.flag.domain.flag.repository;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface FlagRepository {
    // Flag
    Flag save(Flag flag);
    Optional<Flag> findById(Long id);
    Optional<Long> findHostIdById(Long id);
    Optional<Flag> findByIdExclusive(Long id);
    Optional<Flag> findByParentId(Long parentId);
    int expireAllExceedingThreshold(LocalDateTime threshold, LocalDateTime now);
    boolean existsByParentId(Long parentId);
    List<Flag> findAllByIdIn(Collection<Long> ids);
    List<Flag> findAllByHostId(Long hostId);
    List<Flag> findByHostIdsAndDeadlineAfter(Set<Long> hostIds, LocalDateTime asOf);
    List<Flag> findRecentByUserId(Long userId, int limit);

    // FlagParticipant
    FlagParticipant saveParticipant(FlagParticipant participant);
    void deleteParticipant(FlagParticipant participant);
    Optional<FlagParticipant> findParticipant(Long flagId, Long participantId);
    int countParticipants(Long flagId);
    Map<Long, Integer> countParticipantsByFlagIds(Collection<Long> flagIds);
    boolean isParticipating(Long flagId, Long participantId);
    List<Long> findAllParticipantIds(Long flagId);
    List<Long> findFlagIdsByParticipantId(Long participantId);
    List<FlagParticipant> findAllParticipants(Long flagId);
}

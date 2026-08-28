package com.example.DunbarHorizon.flag.domain.flag.repository;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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
    Optional<Flag> findByIdForUpdate(Long id);
    Optional<Flag> findByParentId(Long parentId);
    List<FlagExpiryTarget> findExpiryTargets(LocalDateTime threshold, int limit);
    int expireByIds(Collection<Long> ids, LocalDateTime now);
    boolean existsByParentId(Long parentId);
    List<Flag> findAllByIdIn(Collection<Long> ids);
    Slice<Flag> findAllByHostId(Long hostId, Pageable pageable);
    Slice<Flag> findByParticipantId(Long participantId, Pageable pageable);
    Slice<Flag> findByHostIdsAndDeadlineAfter(Set<Long> hostIds, LocalDateTime asOf, Pageable pageable);
    List<Flag> findByHostIdOrParticipantId(Long userId, int limit);

    // FlagParticipant
    FlagParticipant saveParticipant(FlagParticipant participant);
    void deleteParticipant(FlagParticipant participant);
    Optional<FlagParticipant> findParticipant(Long flagId, Long participantId);
    int countParticipants(Long flagId);
    Map<Long, Integer> countParticipantsByFlagIds(Collection<Long> flagIds);
    boolean isParticipating(Long flagId, Long participantId);
    List<Long> findAllParticipantIds(Long flagId);
    Map<Long, List<Long>> findAllParticipantIdsByFlagIds(Collection<Long> flagIds);
    List<FlagParticipant> findAllParticipants(Long flagId);
}

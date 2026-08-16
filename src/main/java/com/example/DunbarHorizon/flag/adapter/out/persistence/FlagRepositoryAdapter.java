package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagParticipantJpaRepository;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FlagRepositoryAdapter implements FlagRepository {

    private final FlagJpaRepository flagJpaRepository;
    private final FlagParticipantJpaRepository participantJpaRepository;

    // ==================== Flag ====================

    @Override
    public Flag save(Flag flag) {
        return flagJpaRepository.save(flag);
    }

    @Override
    public Optional<Flag> findById(Long id) {
        return flagJpaRepository.findById(id);
    }

    @Override
    public Optional<Long> findHostIdById(Long id) {
        return flagJpaRepository.findHostIdById(id);
    }

    @Override
    public Optional<Flag> findByIdExclusive(Long id) {
        return flagJpaRepository.findByIdExclusive(id);
    }

    @Override
    public Optional<Flag> findByParentId(Long parentId) {
        return flagJpaRepository.findByParentId(parentId);
    }

    @Override
    public boolean existsById(Long id) {
        return flagJpaRepository.existsById(id);
    }

    @Override
    public int expireAllExceedingThreshold(LocalDateTime threshold, LocalDateTime now) {
        return flagJpaRepository.expireAllExceedingThreshold(threshold, now);
    }

    @Override
    public boolean existsByParentId(Long parentId) {
        return flagJpaRepository.existsByParentId(parentId);
    }

    @Override
    public List<Flag> findAllByIdIn(Collection<Long> ids) {
        return flagJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public List<Flag> findAllByHostId(Long hostId) {
        return flagJpaRepository.findAllByHostId(hostId);
    }

    @Override
    public List<Flag> findRecentByUserId(Long userId, int limit) {
        return flagJpaRepository.findRecentByUserId(userId, PageRequest.of(0, limit));
    }

    @Override
    public List<Flag> findByHostIdsAndDeadlineAfter(Set<Long> hostIds, LocalDateTime asOf) {
        if (hostIds == null || hostIds.isEmpty()) {
            return List.of();
        }
        return flagJpaRepository.findByHostIdsAndDeadlineAfter(hostIds, asOf);
    }

    // ==================== FlagParticipant ====================

    @Override
    public FlagParticipant saveParticipant(FlagParticipant participant) {
        return participantJpaRepository.save(participant);
    }

    @Override
    public void deleteParticipant(FlagParticipant participant) {
        participantJpaRepository.delete(participant);
    }

    @Override
    public Optional<FlagParticipant> findParticipant(Long flagId, Long participantId) {
        return participantJpaRepository.findByFlagIdAndParticipantId(flagId, participantId);
    }

    @Override
    public int countParticipants(Long flagId) {
        return participantJpaRepository.countByFlagId(flagId);
    }

    @Override
    public Map<Long, Integer> countParticipantsByFlagIds(Collection<Long> flagIds) {
        if (flagIds == null || flagIds.isEmpty()) return Map.of();
        return participantJpaRepository.countByFlagIdIn(flagIds).stream()
                .collect(Collectors.toMap(
                        FlagParticipantJpaRepository.FlagParticipantCountProjection::getFlagId,
                        p -> p.getCount().intValue()
                ));
    }

    @Override
    public boolean isParticipating(Long flagId, Long participantId) {
        return participantJpaRepository.existsByFlagIdAndParticipantId(flagId, participantId);
    }

    @Override
    public List<Long> findAllParticipantIds(Long flagId) {
        return participantJpaRepository.findAllParticipantIdsByFlagId(flagId);
    }

    @Override
    public void deleteAllParticipants(Long flagId) {
        participantJpaRepository.deleteAllByFlagId(flagId);
    }

    @Override
    public List<Long> findFlagIdsByParticipantId(Long participantId) {
        return participantJpaRepository.findFlagIdsByParticipantId(participantId);
    }

    @Override
    public List<FlagParticipant> findAllParticipants(Long flagId) {
        return participantJpaRepository.findAllByFlagId(flagId);
    }
}

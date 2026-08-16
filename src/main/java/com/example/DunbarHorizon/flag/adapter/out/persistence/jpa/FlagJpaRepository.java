package com.example.DunbarHorizon.flag.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlagJpaRepository extends JpaRepository<Flag, Long> {

    Optional<Flag> findByParentId(Long parentId);

    boolean existsByParentId(Long parentId);

    List<Flag> findAllByIdIn(Collection<Long> ids);

    List<Flag> findAllByHostId(Long hostId);

    @Query("SELECT f.hostId FROM Flag f WHERE f.id = :id")
    Optional<Long> findHostIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flag f WHERE f.id = :id")
    Optional<Flag> findByIdExclusive(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Flag f SET f.deletedAt = :now " +
            "WHERE f.schedule.endDateTime < :threshold " +
            "AND f.autoExpiryExempt = false " +
            "AND f.deletedAt IS NULL")
    int expireAllExceedingThreshold(@Param("threshold") LocalDateTime threshold,
                                    @Param("now") LocalDateTime now);

    @Query("SELECT f FROM Flag f WHERE f.hostId IN :hostIds AND f.schedule.deadline > :asOf")
    List<Flag> findByHostIdsAndDeadlineAfter(@Param("hostIds") Collection<Long> hostIds, @Param("asOf") LocalDateTime asOf);

    @Query(value = "SELECT id FROM flags WHERE deleted_at < :bufferTime LIMIT 5000",
           nativeQuery = true)
    List<Long> _findIdsInternal(@Param("bufferTime") LocalDateTime bufferTime);

    default List<Long> findIdsByDeletedAtBefore(LocalDateTime bufferTime) {
        return _findIdsInternal(bufferTime);
    }

    @Query("SELECT f FROM Flag f " +
           "WHERE f.hostId = :userId " +
           "OR f.id IN (SELECT fp.flagId FROM FlagParticipant fp WHERE fp.participantId = :userId) " +
           "ORDER BY f.createdAt DESC")
    List<Flag> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Flag f WHERE f.id IN :ids")
    void hardDeleteByIdsIn(@Param("ids") Collection<Long> ids);
}
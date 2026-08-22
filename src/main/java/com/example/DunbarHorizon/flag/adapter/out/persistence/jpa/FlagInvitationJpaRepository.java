package com.example.DunbarHorizon.flag.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FlagInvitationJpaRepository extends JpaRepository<FlagInvitation, Long> {

    List<FlagInvitation> findAllByInviteeIdOrderByCreatedAtDesc(Long inviteeId);

    List<FlagInvitation> findAllByInviterIdOrderByCreatedAtDesc(Long inviterId);

    boolean existsByFlagIdAndInviteeId(Long flagId, Long inviteeId);

    @Query("SELECT fi.inviteeId FROM FlagInvitation fi WHERE fi.flagId = :flagId")
    Set<Long> findInviteeIdsByFlagId(@Param("flagId") Long flagId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FlagInvitation fi WHERE fi.flagId IN :flagIds")
    void hardDeleteByFlagIdsIn(@Param("flagIds") Collection<Long> flagIds);

    // Flag의 @SQLRestriction("deleted_at IS NULL")이 서브쿼리에도 적용되어 소프트 삭제된
    // 플래그는 대상에서 빠진다. 그쪽 초대는 purgeFlagsAndRelatedData가 이미 지우므로
    // 의도된 동작이다. 여기서 남는 것은 autoExpiryExempt라 소프트 삭제되지 않는 플래그다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FlagInvitation fi WHERE fi.flagId IN " +
           "(SELECT f.id FROM Flag f WHERE f.schedule.endDateTime < :threshold)")
    int hardDeleteByFlagEndDateTimeBefore(@Param("threshold") LocalDateTime threshold);
}

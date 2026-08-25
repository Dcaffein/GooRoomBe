package com.example.DunbarHorizon.flag.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;



public interface FlagParticipantJpaRepository extends JpaRepository<FlagParticipant, Long> {

    interface FlagParticipantCountProjection {
        Long getFlagId();
        Long getCount();
    }

    @Query("SELECT fp.flagId as flagId, COUNT(fp) as count FROM FlagParticipant fp WHERE fp.flagId IN :flagIds GROUP BY fp.flagId")
    List<FlagParticipantCountProjection> countByFlagIdIn(@Param("flagIds") Collection<Long> flagIds);


    Optional<FlagParticipant> findByFlagIdAndParticipantId(Long flagId, Long participantId);

    boolean existsByFlagIdAndParticipantId(Long flagId, Long participantId);

    int countByFlagId(Long flagId);

    List<FlagParticipant> findAllByFlagId(Long flagId);

    @Query("SELECT fp.participantId FROM FlagParticipant fp WHERE fp.flagId = :flagId")
    List<Long> findAllParticipantIdsByFlagId(@Param("flagId") Long flagId);

    // 단건 조회는 참여자 id만 돌려주면 되지만, 묶음 조회는 어느 플래그의 것인지 함께 와야
    // 플래그별로 나눌 수 있다. 엔티티를 통째로 읽지 않으려고 두 컬럼만 가져온다.
    interface FlagParticipantIdProjection {
        Long getFlagId();
        Long getParticipantId();
    }

    @Query("SELECT fp.flagId AS flagId, fp.participantId AS participantId FROM FlagParticipant fp " +
           "WHERE fp.flagId IN :flagIds")
    List<FlagParticipantIdProjection> findAllParticipantIdsByFlagIdIn(@Param("flagIds") Collection<Long> flagIds);

    @Query("SELECT fp.flagId FROM FlagParticipant fp WHERE fp.participantId = :participantId")
    List<Long> findFlagIdsByParticipantId(@Param("participantId") Long participantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FlagParticipant fp WHERE fp.flagId IN :flagIds")
    void hardDeleteByFlagIdsIn(@Param("flagIds") Collection<Long> flagIds);
}
package com.example.DunbarHorizon.flag.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface FlagMemorialJpaRepository extends JpaRepository<FlagMemorial, Long> {

    boolean existsByFlagId(Long flagId);
    long countByFlagId(Long flagId);
    List<FlagMemorial> findAllByFlagId(Long flagId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FlagMemorial fm WHERE fm.flagId IN :flagIds")
    void hardDeleteByFlagIdsIn(@Param("flagIds") Collection<Long> flagIds);
}
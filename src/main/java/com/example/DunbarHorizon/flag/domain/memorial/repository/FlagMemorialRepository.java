package com.example.DunbarHorizon.flag.domain.memorial.repository;

import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;

import java.util.List;
import java.util.Optional;

public interface FlagMemorialRepository {
    FlagMemorial save(FlagMemorial memorial);
    Optional<FlagMemorial> findById(Long id);
    boolean existsByFlagId(Long flagId);
    long countByFlagId(Long flagId);
    List<FlagMemorial> findAllByFlagId(Long flagId);
    void delete(FlagMemorial memorial);
}
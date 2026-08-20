package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagMemorialJpaRepository;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FlagMemorialRepositoryAdapter implements FlagMemorialRepository {

    private final FlagMemorialJpaRepository jpaRepository;

    @Override
    public FlagMemorial save(FlagMemorial memorial) {
        return jpaRepository.save(memorial);
    }

    @Override
    public Optional<FlagMemorial> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByFlagId(Long flagId) {
        return jpaRepository.existsByFlagId(flagId);
    }

    @Override
    public long countByFlagId(Long flagId) {
        return jpaRepository.countByFlagId(flagId);
    }

    @Override
    public List<FlagMemorial> findAllByFlagId(Long flagId) {
        return jpaRepository.findAllByFlagId(flagId);
    }

    @Override
    public void delete(FlagMemorial memorial) {
        jpaRepository.delete(memorial);
    }
}
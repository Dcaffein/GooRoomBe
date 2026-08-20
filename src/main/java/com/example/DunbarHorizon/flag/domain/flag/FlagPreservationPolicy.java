package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlagPreservationPolicy {

    private final FlagRepository flagRepository;
    private final FlagMemorialRepository memorialRepository;

    public void refresh(Long flagId) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));
        boolean isPreserved = memorialRepository.existsByFlagId(flagId)
                           || flagRepository.existsByParentId(flagId);
        flag.updateAutoExpiryExempt(isPreserved);
    }
}

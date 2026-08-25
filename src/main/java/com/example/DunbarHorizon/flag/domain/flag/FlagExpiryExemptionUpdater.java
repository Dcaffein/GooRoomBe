package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlagExpiryExemptionUpdater {

    private final FlagRepository flagRepository;
    private final FlagMemorialRepository memorialRepository;

    public Flag refresh(Long flagId) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));
        // 후기나 앵코르가 달린 Flag는 자동 만료 스윕에서 뺀다.
        boolean exempt = memorialRepository.existsByFlagId(flagId)
                      || flagRepository.existsByParentId(flagId);
        flag.updateAutoExpiryExempt(exempt);

        return flag;
    }
}

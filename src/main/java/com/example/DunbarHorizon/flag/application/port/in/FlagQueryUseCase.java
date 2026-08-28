package com.example.DunbarHorizon.flag.application.port.in;

import com.example.DunbarHorizon.flag.application.dto.result.FlagDetailResult;
import com.example.DunbarHorizon.flag.application.dto.result.FlagResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface FlagQueryUseCase {
    Slice<FlagResult> getFlagsByRole(Long userId, FlagRole role, Pageable pageable);
    Slice<FlagResult> getFeedFlags(Long userId, Pageable pageable);
    List<FlagResult> getProfileFlags(Long userId);
    FlagDetailResult getFlagDetail(Long flagId, Long viewerId);
}

package com.example.DunbarHorizon.flag.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FlagMaintenancePort {
    List<Long> findIdsReadyForHardDelete(LocalDateTime bufferTime, int batchSize);
    void purgeFlagsAndRelatedData(Collection<Long> flagIds);
}

package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlagHardPurgeService {

    private static final int BUFFER_HOURS = 12;
    private static final int BATCH_SIZE = 5000;

    private final FlagMaintenancePort maintenancePort;

    public void sweepExpiredData() {
        LocalDateTime bufferTime = LocalDateTime.now().minusHours(BUFFER_HOURS);

        List<Long> targets = maintenancePort.findIdsReadyForHardDelete(bufferTime, BATCH_SIZE);

        if (!targets.isEmpty()) {
            maintenancePort.purgeFlagsAndRelatedData(targets);
            log.info("물리 삭제 집행 완료: {}건의 데이터 영구 제거", targets.size());
        }
    }
}
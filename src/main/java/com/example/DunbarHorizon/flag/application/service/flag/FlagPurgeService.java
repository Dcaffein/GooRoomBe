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
public class FlagPurgeService {

    private static final int BUFFER_HOURS = 12;
    private static final int BATCH_SIZE = 5000;

    private final FlagMaintenancePort maintenancePort;

    public void purgeExpiredFlags() {
        LocalDateTime bufferTime = LocalDateTime.now().minusHours(BUFFER_HOURS);

        List<Long> targets = maintenancePort.findIdsReadyForHardDelete(bufferTime, BATCH_SIZE);

        if (!targets.isEmpty()) {
            maintenancePort.purgeFlagsAndRelatedData(targets);
            log.info("퍼지 완료: {}건의 플래그를 관련 데이터와 함께 영구 삭제", targets.size());
        }
    }
}
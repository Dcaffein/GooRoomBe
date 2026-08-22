package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlagExpiryService {

    private final FlagRepository flagRepository;
    private final FlagMaintenancePort maintenancePort;

    @Transactional
    public void expireEndedFlags() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        // 초대 정리가 먼저다. 소프트 삭제를 먼저 하면 Flag의 @SQLRestriction 때문에
        // 방금 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
        int purgedInvitations = maintenancePort.purgeInvitationsOfEndedFlags(threshold);
        int expiredFlags = flagRepository.expireAllExceedingThreshold(threshold, now);

        if (expiredFlags > 0 || purgedInvitations > 0) {
            log.info("자동 만료 완료: 플래그 {}건 소프트 삭제, 종료된 플래그의 초대 {}건 삭제",
                    expiredFlags, purgedInvitations);
        }
    }
}

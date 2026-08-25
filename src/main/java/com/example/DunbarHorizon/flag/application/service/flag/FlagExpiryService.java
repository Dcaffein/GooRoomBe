package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagConcludedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagExpiryTarget;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlagExpiryService {

    private static final int BATCH_SIZE = 5000;

    private final FlagRepository flagRepository;
    private final FlagMaintenancePort maintenancePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void expireEndedFlags() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        // 초대 정리가 먼저다. 소프트 삭제를 먼저 하면 Flag의 @SQLRestriction 때문에
        // 방금 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
        int purgedInvitations = maintenancePort.purgeInvitationsOfEndedFlags(threshold);

        List<FlagExpiryTarget> targets = flagRepository.findExpiryTargets(threshold, BATCH_SIZE);
        List<Long> targetIds = targets.stream().map(FlagExpiryTarget::getId).toList();

        Map<Long, List<Long>> participantsByFlagId =
                flagRepository.findAllParticipantIdsByFlagIds(targetIds);

        int expiredFlags = flagRepository.expireByIds(targetIds, now);

        targets.forEach(target -> publishConclusion(target, participantsByFlagId));

        if (expiredFlags > 0 || purgedInvitations > 0) {
            log.info("자동 만료 완료: 플래그 {}건 소프트 삭제, 종료된 플래그의 초대 {}건 삭제",
                    expiredFlags, purgedInvitations);
        }
    }

    private void publishConclusion(FlagExpiryTarget target, Map<Long, List<Long>> participantsByFlagId) {
        List<Long> participantIds = participantsByFlagId.get(target.getId());
        if (participantIds == null || participantIds.isEmpty()) return;

        eventPublisher.publishEvent(new FlagConcludedEvent(
                target.getId(), target.getHostId(), target.getParentId(), participantIds));
    }
}

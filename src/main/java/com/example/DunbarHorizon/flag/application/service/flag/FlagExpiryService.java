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

    // 밀린 물량이 많아도 한 회차가 가져가는 양을 묶는다. 남은 것은 6시간 뒤 다음 회차 몫이다.
    private static final int BATCH_SIZE = 5000;

    private final FlagRepository flagRepository;
    private final FlagMaintenancePort maintenancePort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 대상은 종료 시각이 24시간 넘게 지났고, 만료 면제가 아니며, 아직 삭제되지 않은 플래그다.
     * 이 조건을 통과했다는 것은 호스트가 취소하지 않은 채로 모임 시간이 지났다는 뜻이므로
     * 소프트 삭제와 함께 종료 사실을 발행한다. 이 경로가 없으면 정상적으로 끝난 모임은
     * 친밀도가 오르지 않는다.
     */
    @Transactional
    public void expireEndedFlags() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        // 초대 정리가 먼저다. 소프트 삭제를 먼저 하면 Flag의 @SQLRestriction 때문에
        // 방금 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
        int purgedInvitations = maintenancePort.purgeInvitationsOfEndedFlags(threshold);

        // 지우기 전에 조회한다. 벌크 UPDATE는 건수만 돌려주는데 플래그마다 이벤트를 발행해야 하고,
        // 소프트 삭제 후에는 @SQLRestriction 때문에 hostId·parentId를 다시 읽을 수 없다.
        List<FlagExpiryTarget> targets = flagRepository.findExpiryTargets(threshold, BATCH_SIZE);
        List<Long> targetIds = targets.stream().map(FlagExpiryTarget::getId).toList();

        Map<Long, List<Long>> participantsByFlagId =
                flagRepository.findAllParticipantIdsByFlagIds(targetIds);

        // 조건을 다시 쓰지 않고 id로 찍는다. 두 번 실행하면 그사이 면제가 붙은 플래그가 생겨
        // 발행한 집합과 삭제한 집합이 어긋날 수 있다.
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

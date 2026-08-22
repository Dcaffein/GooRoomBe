package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagCommentJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagInvitationJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagMemorialJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagParticipantJpaRepository;
import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
public class FlagMaintenanceAdapter implements FlagMaintenancePort {

    private static final int CHUNK_SIZE = 500;

    private final FlagJpaRepository flagJpaRepository;
    private final FlagParticipantJpaRepository participantJpaRepository;
    private final FlagMemorialJpaRepository memorialJpaRepository;
    private final FlagCommentJpaRepository commentJpaRepository;
    private final FlagInvitationJpaRepository invitationJpaRepository;
    private final TransactionTemplate chunkTransaction;

    public FlagMaintenanceAdapter(FlagJpaRepository flagJpaRepository,
                                  FlagParticipantJpaRepository participantJpaRepository,
                                  FlagMemorialJpaRepository memorialJpaRepository,
                                  FlagCommentJpaRepository commentJpaRepository,
                                  FlagInvitationJpaRepository invitationJpaRepository,
                                  PlatformTransactionManager transactionManager) {
        this.flagJpaRepository = flagJpaRepository;
        this.participantJpaRepository = participantJpaRepository;
        this.memorialJpaRepository = memorialJpaRepository;
        this.commentJpaRepository = commentJpaRepository;
        this.invitationJpaRepository = invitationJpaRepository;

        // 청크마다 독립 트랜잭션을 연다. 기본 전파(REQUIRED)면 호출자가 트랜잭션을 열고 있을 때
        // 합류해버려 청크 분리가 무의미해진다.
        this.chunkTransaction = new TransactionTemplate(transactionManager);
        this.chunkTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<Long> findIdsReadyForHardDelete(LocalDateTime bufferTime, int batchSize) {
        return flagJpaRepository.findIdsByDeletedAtBefore(bufferTime, batchSize);
    }

    @Override
    public int purgeInvitationsOfEndedFlags(LocalDateTime threshold) {
        return invitationJpaRepository.hardDeleteByFlagEndDateTimeBefore(threshold);
    }

    @Override
    public void purgeFlagsAndRelatedData(Collection<Long> flagIds) {
        List<Long> idList = new ArrayList<>(flagIds);

        for (int i = 0; i < idList.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, idList.size());
            List<Long> chunk = idList.subList(i, end);

            chunkTransaction.execute(status -> {
                participantJpaRepository.hardDeleteByFlagIdsIn(chunk);
                memorialJpaRepository.hardDeleteByFlagIdsIn(chunk);
                commentJpaRepository.hardDeleteByFlagIdsIn(chunk);
                invitationJpaRepository.hardDeleteByFlagIdsIn(chunk);
                flagJpaRepository.hardDeleteByIdsIn(chunk);
                return null;
            });

            log.debug("물리 삭제 진행 중: {}건 완료", end);
        }
    }
}

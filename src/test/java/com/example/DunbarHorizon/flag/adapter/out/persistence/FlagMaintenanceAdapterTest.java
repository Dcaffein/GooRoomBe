package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagCommentJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagInvitationJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagMemorialJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagParticipantJpaRepository;
import com.example.DunbarHorizon.flag.domain.comment.FlagComment;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 퍼지 어댑터는 청크마다 REQUIRES_NEW로 트랜잭션을 연다. 테스트가 바깥 트랜잭션을 잡고 있으면
 * 새 트랜잭션이 미커밋 데이터를 보지 못하므로 NOT_SUPPORTED로 돌린다. 롤백이 없으니 직접 정리한다.
 */
@JpaRepositoryTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(FlagMaintenanceAdapter.class)
class FlagMaintenanceAdapterTest {

    @Autowired private FlagMaintenanceAdapter adapter;
    @Autowired private FlagJpaRepository flagJpaRepository;
    @Autowired private FlagParticipantJpaRepository participantJpaRepository;
    @Autowired private FlagMemorialJpaRepository memorialJpaRepository;
    @Autowired private FlagCommentJpaRepository commentJpaRepository;
    @Autowired private FlagInvitationJpaRepository invitationJpaRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final Long HOST_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final int BATCH_SIZE = 5000;

    private final List<Long> createdFlagIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 벌크 삭제 쿼리는 트랜잭션을 요구한다. 이 테스트는 NOT_SUPPORTED라 직접 열어준다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            participantJpaRepository.deleteAllInBatch();
            memorialJpaRepository.deleteAllInBatch();
            commentJpaRepository.deleteAllInBatch();
            invitationJpaRepository.deleteAllInBatch();
            if (!createdFlagIds.isEmpty()) {
                flagJpaRepository.hardDeleteByIdsIn(createdFlagIds);
            }
        });
        createdFlagIds.clear();
    }

    /** deletedAt을 과거로 박아 저장한다. 소프트 삭제 후 버퍼가 지난 상태를 만든다. */
    private Flag persistSoftDeletedFlag(LocalDateTime deletedAt) {
        LocalDateTime past = LocalDateTime.now().minusDays(5);
        Flag flag = Flag.create(HOST_ID, "지난 플래그", "설명", 10,
                FlagSchedule.of(past, past.plusHours(1), past.plusHours(2)));
        ReflectionTestUtils.setField(flag, "deletedAt", deletedAt);
        Flag saved = flagJpaRepository.save(flag);
        createdFlagIds.add(saved.getId());
        return saved;
    }

    private Flag persistLiveFlag() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        Flag flag = Flag.create(HOST_ID, "살아있는 플래그", "설명", 10,
                FlagSchedule.of(future, future.plusHours(1), future.plusHours(2)));
        Flag saved = flagJpaRepository.save(flag);
        createdFlagIds.add(saved.getId());
        return saved;
    }

    /** FlagParticipant·FlagMemorial의 생성자는 package-private이라 테스트 시딩용으로만 리플렉션을 쓴다. */
    private static <T> T newInstance(Class<T> type, Class<?>[] paramTypes, Object... args) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 생성 실패", e);
        }
    }

    private void persistChildren(Long flagId) {
        participantJpaRepository.save(
                newInstance(FlagParticipant.class, new Class<?>[]{Long.class, Long.class}, flagId, MEMBER_ID));
        memorialJpaRepository.save(
                newInstance(FlagMemorial.class, new Class<?>[]{Long.class, Long.class, String.class},
                        flagId, MEMBER_ID, "후기 내용"));
        commentJpaRepository.save(FlagComment.createRoot(flagId, MEMBER_ID, "댓글 내용", false));
        invitationJpaRepository.save(FlagInvitation.create(flagId, HOST_ID, MEMBER_ID));
    }

    @Test
    @DisplayName("소프트 삭제된 Flag와 자식 데이터가 다섯 테이블에서 모두 사라진다")
    void purge_RemovesFlagAndAllChildren() {
        // given
        Flag target = persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));
        persistChildren(target.getId());

        assertThat(participantJpaRepository.count()).isEqualTo(1);
        assertThat(memorialJpaRepository.count()).isEqualTo(1);
        assertThat(commentJpaRepository.count()).isEqualTo(1);
        assertThat(invitationJpaRepository.count()).isEqualTo(1);

        // when
        adapter.purgeFlagsAndRelatedData(List.of(target.getId()));

        // then — Flag는 @SQLRestriction 때문에 findById로 확인할 수 없으므로 네이티브 조회로 본다
        assertThat(adapter.findIdsReadyForHardDelete(LocalDateTime.now(), BATCH_SIZE))
                .doesNotContain(target.getId());
        assertThat(participantJpaRepository.count()).isZero();
        assertThat(memorialJpaRepository.count()).isZero();
        assertThat(commentJpaRepository.count()).isZero();
        assertThat(invitationJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("참여자는 하드 퍼지에서만 정리된다")
    void purge_IsTheOnlyCleanupForParticipants() {
        // given — 즉시 삭제를 없앴으므로 이 경로가 유일한 정리 수단이다
        Flag target = persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));
        participantJpaRepository.save(
                newInstance(FlagParticipant.class, new Class<?>[]{Long.class, Long.class},
                        target.getId(), MEMBER_ID));

        // when
        adapter.purgeFlagsAndRelatedData(List.of(target.getId()));

        // then
        assertThat(participantJpaRepository.existsByFlagIdAndParticipantId(target.getId(), MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("삭제되지 않은 Flag는 퍼지 대상에 포함되지 않는다")
    void findIdsReadyForHardDelete_ExcludesLiveFlags() {
        // given
        Flag live = persistLiveFlag();
        Flag deleted = persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));

        // when
        List<Long> targets = adapter.findIdsReadyForHardDelete(LocalDateTime.now(), BATCH_SIZE);

        // then
        assertThat(targets).contains(deleted.getId()).doesNotContain(live.getId());
    }

    @Test
    @DisplayName("버퍼가 지나지 않은 Flag는 퍼지 대상에 포함되지 않는다")
    void findIdsReadyForHardDelete_RespectsBuffer() {
        // given
        Flag justDeleted = persistSoftDeletedFlag(LocalDateTime.now().minusHours(1));
        Flag longDeleted = persistSoftDeletedFlag(LocalDateTime.now().minusDays(2));

        // when — 12시간 버퍼
        List<Long> targets = adapter.findIdsReadyForHardDelete(
                LocalDateTime.now().minusHours(12), BATCH_SIZE);

        // then
        assertThat(targets).contains(longDeleted.getId()).doesNotContain(justDeleted.getId());
    }

    @Test
    @DisplayName("batchSize가 조회 건수를 제한한다")
    void findIdsReadyForHardDelete_LimitsByBatchSize() {
        // given
        persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));
        persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));
        persistSoftDeletedFlag(LocalDateTime.now().minusDays(1));

        // when
        List<Long> targets = adapter.findIdsReadyForHardDelete(LocalDateTime.now(), 2);

        // then
        assertThat(targets).hasSize(2);
    }
}

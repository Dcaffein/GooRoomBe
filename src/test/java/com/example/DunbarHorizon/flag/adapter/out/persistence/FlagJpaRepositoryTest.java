package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagJpaRepository;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagExpiryTarget;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@JpaRepositoryTest
class FlagJpaRepositoryTest {

    @Autowired private FlagJpaRepository repository;
    @Autowired private TestEntityManager em;

    private static final Long HOST_ID = 10L;
    private static final Long OTHER_HOST_ID = 20L;
    private static final PageRequest LATEST_PAGE = PageRequest.of(
            0, 100, Sort.by(Sort.Direction.DESC, "createdAt")
    );

    /**
     * DB의 datetime 정밀도에 따라 나노초가 잘려 경계 비교가 흔들리는 것을 막는다.
     * '마감 정각' 케이스를 검증하려면 저장값과 비교값이 정확히 같아야 한다.
     */
    private final LocalDateTime asOf = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    private Flag persistFlag(Long hostId, LocalDateTime deadline, LocalDateTime start, LocalDateTime end) {
        Flag flag = Flag.create(hostId, "테스트 플래그", "설명", 10, FlagSchedule.of(deadline, start, end));
        em.persist(flag);
        return flag;
    }

    private void persistParticipant(Long flagId, Long participantId) {
        FlagParticipant participant = BeanUtils.instantiateClass(FlagParticipant.class);
        ReflectionTestUtils.setField(participant, "flagId", flagId);
        ReflectionTestUtils.setField(participant, "participantId", participantId);
        em.persist(participant);
    }

    @Test
    @DisplayName("호스트의 플래그 중 마감이 기준 시각보다 뒤인 것만 조회한다")
    void findByHostIdsAndDeadlineAfter_ReturnsOnlyFlagsWithFutureDeadline() {
        // given
        Flag future = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(HOST_ID, asOf, asOf.plusHours(2), asOf.plusHours(3));                          // 마감 정각 — 제외 대상
        persistFlag(HOST_ID, asOf.minusHours(1), asOf.plusHours(2), asOf.plusHours(3));            // 마감 지남 — 제외 대상
        persistFlag(HOST_ID, asOf.minusHours(5), asOf.minusHours(4), asOf.minusHours(3));          // 종료됨 — 제외 대상
        persistFlag(OTHER_HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));       // 다른 호스트 — 제외 대상
        em.flush();
        em.clear();

        // when
        List<Flag> results = repository
                .findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf, LATEST_PAGE)
                .getContent();

        // then
        assertThat(results).extracting(Flag::getId).containsExactly(future.getId());
    }

    @Test
    @DisplayName("조회 결과는 도메인이 RECRUITING으로 판단하는 집합과 정확히 일치한다")
    void findByHostIdsAndDeadlineAfter_MatchesDomainRecruitingClassification() {
        // given — 마감·시작·종료 경계를 기준 시각 앞뒤로 흩어놓는다
        persistFlag(HOST_ID, asOf.plusSeconds(1), asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(HOST_ID, asOf, asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(HOST_ID, asOf.minusSeconds(1), asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(HOST_ID, asOf.minusHours(1), asOf, asOf.plusHours(3));
        persistFlag(HOST_ID, asOf.minusHours(2), asOf.minusHours(1), asOf.plusHours(1));
        persistFlag(HOST_ID, asOf.minusHours(3), asOf.minusHours(2), asOf);
        persistFlag(HOST_ID, asOf.minusHours(5), asOf.minusHours(4), asOf.minusHours(3));
        em.flush();
        em.clear();

        List<Flag> allFlags = repository.findAllByHostId(HOST_ID, LATEST_PAGE).getContent();
        List<Long> recruitingByDomain = allFlags.stream()
                .filter(f -> f.getSchedule().calculateStatus(asOf) == FlagStatus.RECRUITING)
                .map(Flag::getId)
                .toList();

        // when
        List<Flag> recruitingByQuery = repository
                .findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf, LATEST_PAGE)
                .getContent();

        // then
        assertThat(recruitingByDomain).isNotEmpty();
        assertThat(recruitingByQuery).extracting(Flag::getId)
                .containsExactlyInAnyOrderElementsOf(recruitingByDomain);
    }

    @Test
    @DisplayName("소프트 삭제된 플래그는 마감이 남아 있어도 조회되지 않는다")
    void findByHostIdsAndDeadlineAfter_ExcludesSoftDeletedFlags() {
        // given
        Flag alive = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag deleted = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        deleted.softDelete();
        em.flush();
        em.clear();

        // when
        List<Flag> results = repository
                .findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf, LATEST_PAGE)
                .getContent();

        // then
        assertThat(results).extracting(Flag::getId).containsExactly(alive.getId());
    }

    @Test
    @DisplayName("여러 호스트의 모집 중 플래그를 한 번에 조회한다")
    void findByHostIdsAndDeadlineAfter_ReturnsFlagsForAllGivenHosts() {
        // given
        Flag hostFlag = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag otherHostFlag = persistFlag(OTHER_HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        persistFlag(30L, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));   // 대상 아님
        em.flush();
        em.clear();

        // when
        List<Flag> results = repository
                .findByHostIdsAndDeadlineAfter(Set.of(HOST_ID, OTHER_HOST_ID), asOf, LATEST_PAGE)
                .getContent();

        // then
        assertThat(results).extracting(Flag::getId)
                .containsExactlyInAnyOrder(hostFlag.getId(), otherHostFlag.getId());
    }

    @Test
    @DisplayName("호스트 Flag를 최신순으로 페이지 조회한다")
    void findAllByHostId_ReturnsLatestSlice() {
        // given
        Flag oldest = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag middle = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag newest = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        ReflectionTestUtils.setField(oldest, "createdAt", asOf.minusHours(3));
        ReflectionTestUtils.setField(middle, "createdAt", asOf.minusHours(2));
        ReflectionTestUtils.setField(newest, "createdAt", asOf.minusHours(1));
        em.flush();
        em.clear();

        // when
        var result = repository.findAllByHostId(
                HOST_ID,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        // then
        assertThat(result.getContent()).extracting(Flag::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("참여자 ID로 Flag를 직접 페이지 조회한다")
    void findByParticipantId_ReturnsParticipatingFlags() {
        // given
        Flag participating = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag unrelated = persistFlag(OTHER_HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        em.flush();
        persistParticipant(participating.getId(), 99L);
        em.flush();
        em.clear();

        // when
        var result = repository.findByParticipantId(99L, LATEST_PAGE);

        // then
        assertThat(result.getContent()).extracting(Flag::getId)
                .containsExactly(participating.getId())
                .doesNotContain(unrelated.getId());
    }

    @Test
    @DisplayName("프로필 Flag는 주최와 참여 목록을 합쳐 최신순 제한 조회한다")
    void findByHostIdOrParticipantId_ReturnsLatestHostedAndParticipatingFlags() {
        // given
        Long profileUserId = 99L;
        Flag hosted = persistFlag(profileUserId, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag participating = persistFlag(HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        Flag unrelated = persistFlag(OTHER_HOST_ID, asOf.plusHours(1), asOf.plusHours(2), asOf.plusHours(3));
        ReflectionTestUtils.setField(hosted, "createdAt", asOf.minusHours(2));
        ReflectionTestUtils.setField(participating, "createdAt", asOf.minusHours(1));
        ReflectionTestUtils.setField(unrelated, "createdAt", asOf);
        em.flush();
        persistParticipant(participating.getId(), profileUserId);
        em.flush();
        em.clear();

        // when
        List<Flag> result = repository.findByHostIdOrParticipantId(
                profileUserId, PageRequest.of(0, 2)
        );

        // then
        assertThat(result).extracting(Flag::getId)
                .containsExactly(participating.getId(), hosted.getId())
                .doesNotContain(unrelated.getId());
    }

    private Flag persistEndedFlag(Long hostId, LocalDateTime end) {
        return persistFlag(hostId, end.minusHours(3), end.minusHours(2), end);
    }

    @Test
    @DisplayName("만료 대상은 종료 시각이 지났고 만료 면제가 아닌 플래그뿐이다")
    void findExpiryTargets_ReturnsOnlyEndedAndNotExempt() {
        // given
        Flag target = persistEndedFlag(HOST_ID, asOf.minusHours(1));
        Flag notEndedYet = persistEndedFlag(HOST_ID, asOf.plusHours(1));

        Flag exempt = persistEndedFlag(HOST_ID, asOf.minusHours(2));
        ReflectionTestUtils.setField(exempt, "autoExpiryExempt", true);

        Flag alreadyDeleted = persistEndedFlag(HOST_ID, asOf.minusHours(3));
        alreadyDeleted.softDelete();

        em.flush();
        em.clear();

        // when
        List<FlagExpiryTarget> results = repository.findExpiryTargets(asOf, PageRequest.of(0, 100));

        // then — 통합 테스트가 같은 컨테이너에 커밋해둔 행이 섞일 수 있어 포함 여부로만 본다
        assertThat(results).extracting(FlagExpiryTarget::getId).contains(target.getId());
        assertThat(results).extracting(FlagExpiryTarget::getId)
                .doesNotContain(notEndedYet.getId(), exempt.getId(), alreadyDeleted.getId());
    }

    @Test
    @DisplayName("만료 대상 조회가 hostId와 parentId를 함께 돌려준다")
    void findExpiryTargets_CarriesHostIdAndParentId() {
        // given
        Flag parent = persistEndedFlag(HOST_ID, asOf.minusHours(5));
        ReflectionTestUtils.setField(parent, "autoExpiryExempt", true);
        em.flush();

        Flag encore = persistEndedFlag(OTHER_HOST_ID, asOf.minusHours(1));
        ReflectionTestUtils.setField(encore, "parentId", parent.getId());
        em.flush();
        em.clear();

        // when
        List<FlagExpiryTarget> results = repository.findExpiryTargets(asOf, PageRequest.of(0, 100));

        // then
        FlagExpiryTarget found = results.stream()
                .filter(t -> t.getId().equals(encore.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getHostId()).isEqualTo(OTHER_HOST_ID);
        assertThat(found.getParentId()).isEqualTo(parent.getId());
        assertThat(results).extracting(FlagExpiryTarget::getId).doesNotContain(parent.getId());
    }

    @Test
    @DisplayName("만료 대상 조회에 상한이 걸린다")
    void findExpiryTargets_HonorsLimit() {
        // given
        persistEndedFlag(HOST_ID, asOf.minusHours(1));
        persistEndedFlag(HOST_ID, asOf.minusHours(2));
        persistEndedFlag(HOST_ID, asOf.minusHours(3));
        em.flush();
        em.clear();

        // when
        List<FlagExpiryTarget> results = repository.findExpiryTargets(asOf, PageRequest.of(0, 2));

        // then
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("expireByIds는 넘긴 id만 소프트 삭제한다")
    void expireByIds_SoftDeletesGivenIdsOnly() {
        // given
        Flag target = persistEndedFlag(HOST_ID, asOf.minusHours(1));
        Flag untouched = persistEndedFlag(HOST_ID, asOf.minusHours(2));
        em.flush();
        em.clear();

        // when
        int affected = repository.expireByIds(List.of(target.getId()), asOf);
        em.clear();

        // then
        assertThat(affected).isEqualTo(1);
        assertThat(repository.findById(target.getId())).isEmpty();
        assertThat(repository.findById(untouched.getId())).isPresent();
    }
}

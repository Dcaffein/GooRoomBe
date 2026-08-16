package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagJpaRepository;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

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
        List<Flag> results = repository.findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf);

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

        List<Flag> allFlags = repository.findAllByHostId(HOST_ID);
        List<Long> recruitingByDomain = allFlags.stream()
                .filter(f -> f.getSchedule().calculateStatus(asOf) == FlagStatus.RECRUITING)
                .map(Flag::getId)
                .toList();

        // when
        List<Flag> recruitingByQuery = repository.findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf);

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
        List<Flag> results = repository.findByHostIdsAndDeadlineAfter(Set.of(HOST_ID), asOf);

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
        List<Flag> results = repository.findByHostIdsAndDeadlineAfter(Set.of(HOST_ID, OTHER_HOST_ID), asOf);

        // then
        assertThat(results).extracting(Flag::getId)
                .containsExactlyInAnyOrder(hostFlag.getId(), otherHostFlag.getId());
    }
}
